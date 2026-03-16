package com.finsight.backend.service;

import com.finsight.backend.dto.BankTransactionDto;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.dto.SyncStatus;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Category;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BankStatementService {

    private static final Logger log = LoggerFactory.getLogger(BankStatementService.class);

    private final GeminiStatementParserService geminiStatementParserService;
    private final CsvStatementParser csvStatementParser;
    private final BankTransactionRepository bankTransactionRepository;
    private final CategoryRepository categoryRepository;
    private final VendorManager vendorManager;
    private final BankTransactionCategorizationService categorizationService;
    private final ReconciliationService reconciliationService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final Map<String, SyncStatus> uploadStatuses = new ConcurrentHashMap<>();

    public BankStatementService(GeminiStatementParserService geminiStatementParserService,
                                CsvStatementParser csvStatementParser,
                                BankTransactionRepository bankTransactionRepository,
                                CategoryRepository categoryRepository,
                                VendorManager vendorManager,
                                BankTransactionCategorizationService categorizationService,
                                ReconciliationService reconciliationService,
                                AnomalyDetectionService anomalyDetectionService) {
        this.geminiStatementParserService = geminiStatementParserService;
        this.csvStatementParser = csvStatementParser;
        this.bankTransactionRepository = bankTransactionRepository;
        this.categoryRepository = categoryRepository;
        this.vendorManager = vendorManager;
        this.categorizationService = categorizationService;
        this.reconciliationService = reconciliationService;
        this.anomalyDetectionService = anomalyDetectionService;
    }

    @Transactional(readOnly = true)
    public Page<BankTransactionDto> getPagedTransactions(String tenantId, Pageable pageable, Boolean reconciled) {
        if (reconciled != null) {
            return bankTransactionRepository.findByTenantIdWithCategoryAndReconciled(tenantId, reconciled, pageable)
                    .map(BankTransactionDto::from);
        }
        return bankTransactionRepository.findByTenantIdWithCategory(tenantId, pageable)
                .map(BankTransactionDto::from);
    }

    @Transactional
    public BankTransactionDto updateTransaction(Long id, BankTransactionDto dto) {
        BankTransaction txn = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found with ID: " + id));

        if (dto.getTxDate() != null) txn.setTxDate(dto.getTxDate());
        if (dto.getDescription() != null) txn.setDescription(dto.getDescription());
        if (dto.getVendor() != null) txn.setVendor(dto.getVendor());
        if (dto.getAmount() != null) txn.setAmount(dto.getAmount());
        if (dto.getType() != null) {
            try {
                txn.setType(BankTransaction.TransactionType.valueOf(dto.getType().toUpperCase()));
            } catch (Exception e) {
                // Ignore invalid type
            }
        }

        // Re-generate reference number if core fields change? Probably best to keep it or update it.
        // For simplicity, we just save the entity. The reference number is primarily for deduplication on upload.

        BankTransaction updatedTxn = bankTransactionRepository.save(txn);
        return BankTransactionDto.from(updatedTxn);
    }

    public SyncStatus getUploadStatus(String tenantId) {
        return uploadStatuses.getOrDefault(tenantId, new SyncStatus());
    }

    public void processStatementAsync(String tenantId, MultipartFile file) {
        SyncStatus status = uploadStatuses.computeIfAbsent(tenantId, k -> new SyncStatus());
        if ("RUNNING".equals(status.getStatus())) return;

        status.setStatus("RUNNING");
        status.setStage("INITIALIZING");
        status.setMessage("Starting file processing...");
        status.setProcessedFiles(0);
        status.setTotalFiles(0);
        status.getLogs().clear();
        status.addLog("INFO: Statement upload process started.");

        // Senior Dev Fix: Copy to a persistent temp file synchronously 
        // because Tomcat's MultipartFile temp file is deleted after the request thread finishes.
        File persistentTempFile;
        try {
            persistentTempFile = File.createTempFile("finsight_upload_", file.getOriginalFilename());
            Files.copy(file.getInputStream(), persistentTempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.error("Failed to create local copy of uploaded file", e);
            status.setStatus("ERROR");
            status.setMessage("FileSystem Error: " + e.getMessage());
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
                List<ParsedBankTransactionDto> parsedTxns;

                status.setStage("EXTRACTION");
                status.setMessage("Extracting transactions from " + (filename.endsWith(".csv") ? "CSV" : "PDF") + "...");
                
                if (filename.endsWith(".csv")) {
                    parsedTxns = csvStatementParser.parse(persistentTempFile);
                } else {
                    parsedTxns = geminiStatementParserService.parsePdfStatement(persistentTempFile);
                }

                status.setTotalFiles(parsedTxns.size());
                status.setMessage("Extracted " + parsedTxns.size() + " transactions. Persisting to database...");
                status.setStage("PERSISTENCE");

                int savedCount = persistParsedTransactionsWithStatus(parsedTxns, !filename.endsWith(".csv"), status);

                status.setStatus("SUCCESS");
                status.setStage("COMPLETED");
                status.setMessage("Successfully processed " + savedCount + " new transactions.");
                status.setProcessedFiles(savedCount);
                status.addLog("SUCCESS: Completed statement processing.");

                if (savedCount > 0) {
                    status.setStage("POST_PROCESSING");
                    status.setMessage("Running AI Reconciliation and Anomaly Detection...");
                    try {
                        reconciliationService.runReconciliation();
                        anomalyDetectionService.detectAnomalies();
                        status.addLog("INFO: AI Post-processing complete.");
                    } catch (Exception ex) {
                        log.error("Failed post-processing", ex);
                        status.addLog("WARN: AI Post-processing failed: " + ex.getMessage());
                    }
                }

            } catch (Exception e) {
                log.error("Error processing statement asynchronously", e);
                status.setStatus("ERROR");
                status.setStage("FAILED");
                status.setMessage("Processing failed: " + e.getMessage());
                status.addLog("ERROR: " + e.getMessage());
            } finally {
                // Cleanup the persistent temp file
                if (persistentTempFile != null && persistentTempFile.exists()) {
                    persistentTempFile.delete();
                }
            }
        });
    }

    @Transactional
    public int processPdfStatement(MultipartFile file) throws Exception {
        log.info("Processing Bank Statement PDF: {}", file.getOriginalFilename());
        File tmpDir = new File(System.getProperty("java.io.tmpdir", "tmp"));
        if (!tmpDir.exists()) tmpDir.mkdirs();
        File temp = File.createTempFile("finsight_sync_", ".pdf", tmpDir);
        Files.copy(file.getInputStream(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            List<ParsedBankTransactionDto> parsedTxns = geminiStatementParserService.parsePdfStatement(temp);
            log.info("Parser extracted {} transactions from PDF.", parsedTxns.size());
            return persistParsedTransactions(parsedTxns, true);
        } finally {
            temp.delete();
        }
    }

    @Transactional
    public int processCsvStatement(MultipartFile file) throws Exception {
        log.info("Processing Bank Statement CSV: {}", file.getOriginalFilename());
        File temp = File.createTempFile("finsight_sync_", ".csv");
        Files.copy(file.getInputStream(), temp.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            List<ParsedBankTransactionDto> parsedTxns = csvStatementParser.parse(temp);
            log.info("Parser extracted {} transactions from CSV.", parsedTxns.size());
            return persistParsedTransactions(parsedTxns, false);
        } finally {
            temp.delete();
        }
    }

    private int persistParsedTransactions(List<ParsedBankTransactionDto> parsedTxns, boolean useSha256) {
        return persistParsedTransactionsWithStatus(parsedTxns, useSha256, null);
    }

    private int persistParsedTransactionsWithStatus(List<ParsedBankTransactionDto> parsedTxns, boolean useSha256, SyncStatus status) {
        int totalToProcess = parsedTxns.size();
        List<BankTransaction> toSave = new ArrayList<>();
        int current = 0;

        for (ParsedBankTransactionDto dto : parsedTxns) {
            current++;
            if (status != null) {
                status.setMessage("Validating transaction " + current + " of " + totalToProcess + "...");
            }
            
            if (dto.getTxDate() == null || dto.getAmount() == null || dto.getDescription() == null) {
                log.warn("Skipping invalid transaction: {}", dto.getDescription());
                continue;
            }

            LocalDate txDate = csvStatementParser.parseDateRobustly(dto.getTxDate());
            if (txDate == null) continue;

            BankTransaction txn = new BankTransaction();
            txn.setTxDate(txDate);
            txn.setDescription(dto.getDescription());
            txn.setVendor(dto.getVendor() != null ? dto.getVendor() : "Unknown");
            txn.setAmount(dto.getAmount());
            txn.setTenantId("local_tenant"); // Senior Fix: Explicitly setting tenantId

            try {
                txn.setType(BankTransaction.TransactionType.valueOf(dto.getType().toUpperCase()));
            } catch (Exception e) {
                txn.setType(BankTransaction.TransactionType.DEBIT);
            }

            // Categorization Engine: 3-tier pipeline
            // 1. Use Gemini's parse-time category if specific
            // 2. Keyword rules   3. AI classification fallback
            String categoryName = categorizationService.categorize(
                    txn.getVendor(),
                    txn.getDescription(),
                    dto.getCategory(),
                    dto.getType()
            );
            Category category = categorizationService.getOrCreateCategoryEntity(categoryName, dto.getType());
            txn.setCategory(category);

            String rawRef = txDate + "|" + txn.getAmount() + "|" + txn.getDescription();
            txn.setReferenceNumber(useSha256 ? sha256Short(rawRef) : Integer.toHexString(rawRef.hashCode()));

            if (!bankTransactionRepository.existsByReferenceNumberAndTenantId(txn.getReferenceNumber(), txn.getTenantId())) {
                toSave.add(txn);
            }
        }

        if (!toSave.isEmpty()) {
            log.info("Batch saving {} transactions...", toSave.size());
            bankTransactionRepository.saveAll(toSave);
            
            // Background update vendor stats
            CompletableFuture.runAsync(() -> {
                for (BankTransaction txn : toSave) {
                    vendorManager.updateVendorStats(txn.getTenantId(), txn.getVendor(), txn.getAmount(), txn.getTxDate());
                }
            });
        }

        return toSave.size();
    }

    private Category getOrCreateCategory(String name, Category.CategoryType type) {
        return categoryRepository.findByNameAndTenantId(name, "local_tenant")
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName(name);
                    c.setType(type);
                    return categoryRepository.save(c);
                });
    }

    private String sha256Short(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException e) {
            return Long.toHexString(input.hashCode() & 0xFFFFFFFFL);
        }
    }
}
