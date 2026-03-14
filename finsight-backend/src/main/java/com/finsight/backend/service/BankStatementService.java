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

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final Map<String, SyncStatus> uploadStatuses = new ConcurrentHashMap<>();

    public BankStatementService(GeminiStatementParserService geminiStatementParserService,
                                CsvStatementParser csvStatementParser,
                                BankTransactionRepository bankTransactionRepository,
                                CategoryRepository categoryRepository,
                                VendorManager vendorManager) {
        this.geminiStatementParserService = geminiStatementParserService;
        this.csvStatementParser = csvStatementParser;
        this.bankTransactionRepository = bankTransactionRepository;
        this.categoryRepository = categoryRepository;
        this.vendorManager = vendorManager;
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

        CompletableFuture.runAsync(() -> {
            try {
                String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
                List<ParsedBankTransactionDto> parsedTxns;

                status.setStage("EXTRACTION");
                status.setMessage("Extracting transactions from " + (filename.endsWith(".csv") ? "CSV" : "PDF") + "...");
                
                if (filename.endsWith(".csv")) {
                    parsedTxns = csvStatementParser.parse(file);
                } else {
                    parsedTxns = geminiStatementParserService.parsePdfStatement(file);
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

            } catch (Exception e) {
                log.error("Error processing statement asynchronously", e);
                status.setStatus("ERROR");
                status.setStage("FAILED");
                status.setMessage("Processing failed: " + e.getMessage());
                status.addLog("ERROR: " + e.getMessage());
            }
        });
    }

    @Transactional
    public int processPdfStatement(MultipartFile file) throws Exception {
        log.info("Processing Bank Statement PDF: {}", file.getOriginalFilename());
        List<ParsedBankTransactionDto> parsedTxns = geminiStatementParserService.parsePdfStatement(file);
        log.info("Parser extracted {} transactions from PDF.", parsedTxns.size());
        return persistParsedTransactions(parsedTxns, true);
    }

    @Transactional
    public int processCsvStatement(MultipartFile file) throws Exception {
        log.info("Processing Bank Statement CSV: {}", file.getOriginalFilename());
        List<ParsedBankTransactionDto> parsedTxns = csvStatementParser.parse(file);
        log.info("Parser extracted {} transactions from CSV.", parsedTxns.size());
        return persistParsedTransactions(parsedTxns, false);
    }

    private int persistParsedTransactions(List<ParsedBankTransactionDto> parsedTxns, boolean useSha256) {
        return persistParsedTransactionsWithStatus(parsedTxns, useSha256, null);
    }

    private int persistParsedTransactionsWithStatus(List<ParsedBankTransactionDto> parsedTxns, boolean useSha256, SyncStatus status) {
        int savedCount = 0;
        int totalToProcess = parsedTxns.size();
        int current = 0;

        for (ParsedBankTransactionDto dto : parsedTxns) {
            current++;
            if (status != null) {
                status.setMessage("Saving transaction " + current + " of " + totalToProcess + "...");
            }
            if (dto.getTxDate() == null || dto.getAmount() == null || dto.getDescription() == null) {
                log.warn("Skipping invalid transaction — missing mandatory fields: {}", dto.getDescription());
                continue;
            }

            LocalDate txDate = csvStatementParser.parseDateRobustly(dto.getTxDate());
            if (txDate == null) {
                log.warn("Skipping row: Cannot parse date '{}'", dto.getTxDate());
                continue;
            }

            BankTransaction txn = new BankTransaction();
            txn.setTxDate(txDate);
            txn.setDescription(dto.getDescription());
            txn.setVendor(dto.getVendor() != null ? dto.getVendor() : "Unknown");
            txn.setAmount(dto.getAmount());

            try {
                txn.setType(BankTransaction.TransactionType.valueOf(dto.getType().toUpperCase()));
            } catch (Exception e) {
                log.warn("Invalid transaction type '{}', defaulting to DEBIT.", dto.getType());
                txn.setType(BankTransaction.TransactionType.DEBIT);
            }

            if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
                Category category = getOrCreateCategory(dto.getCategory().trim(),
                        txn.getType() == BankTransaction.TransactionType.CREDIT
                                ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE);
                txn.setCategory(category);
            }

            String rawRef = txDate + "|" + txn.getAmount() + "|" + txn.getDescription();
            txn.setReferenceNumber(useSha256 ? sha256Short(rawRef) : Integer.toHexString(rawRef.hashCode()));

            boolean exists = bankTransactionRepository.existsByReferenceNumberAndTenantId(
                    txn.getReferenceNumber(), txn.getTenantId());
            if (!exists) {
                bankTransactionRepository.save(txn);
                
                // Update Vendor stats
                vendorManager.updateVendorStats(txn.getTenantId(), txn.getVendor(), txn.getAmount(), txn.getTxDate());
                
                savedCount++;
            } else {
                log.debug("Skipped duplicate transaction: {}", dto.getDescription());
            }
        }
        log.info("Persisted {} new transactions.", savedCount);
        return savedCount;
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
