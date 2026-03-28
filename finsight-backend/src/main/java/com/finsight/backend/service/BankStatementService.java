package com.finsight.backend.service;

import com.finsight.backend.dto.BankTransactionDto;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.dto.SyncStatus;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Category;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.StatementUpload;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.StatementUploadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.finsight.backend.util.RuntimeErrorLogger;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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
    private final XlsxStatementParser xlsxStatementParser;
    private final BankTransactionRepository bankTransactionRepository;
    private final BankTransactionCategorizationService categorizationService;
    private final TransactionPatternEnricher transactionPatternEnricher;
    private final StatementUploadRepository statementUploadRepository;
    private final AppConfigService appConfigService;
    private final VendorManager vendorManager;
    private String uploadBaseDir = "app-data/uploads";

    private final Map<String, SyncStatus> uploadStatuses = new ConcurrentHashMap<>();

    public BankStatementService(GeminiStatementParserService geminiStatementParserService,
                                CsvStatementParser csvStatementParser,
                                XlsxStatementParser xlsxStatementParser,
                                BankTransactionRepository bankTransactionRepository,
                                BankTransactionCategorizationService categorizationService,
                                TransactionPatternEnricher transactionPatternEnricher,
                                StatementUploadRepository statementUploadRepository,
                                AppConfigService appConfigService,
                                VendorManager vendorManager) {
        this.geminiStatementParserService = geminiStatementParserService;
        this.csvStatementParser = csvStatementParser;
        this.xlsxStatementParser = xlsxStatementParser;
        this.bankTransactionRepository = bankTransactionRepository;
        this.categorizationService = categorizationService;
        this.transactionPatternEnricher = transactionPatternEnricher;
        this.statementUploadRepository = statementUploadRepository;
        this.appConfigService = appConfigService;
        this.vendorManager = vendorManager;
    }

    public void setUploadBaseDir(String uploadBaseDir) {
        this.uploadBaseDir = uploadBaseDir;
    }

    public Page<BankTransactionDto> getPagedTransactions(String tenantId, Pageable pageable, Boolean reconciled, String type, String startDate, String endDate, String accountType) {
        BankTransaction.AccountType accType = parseAccountType(accountType);
        BankTransaction.TransactionType txType = null;
        if (type != null && !type.trim().isEmpty()) {
            try {
                txType = BankTransaction.TransactionType.valueOf(type.trim().toUpperCase());
            } catch (Exception e) {
                log.warn("Invalid transaction type: {}", type);
            }
        }

        LocalDate start = null;
        LocalDate end = null;
        if (startDate != null && !startDate.isBlank()) {
            try {
                start = LocalDate.parse(startDate);
            } catch (Exception e) {
                log.warn("Invalid startDate format: {}", startDate);
            }
        }
        if (endDate != null && !endDate.isBlank()) {
            try {
                end = LocalDate.parse(endDate);
            } catch (Exception e) {
                log.warn("Invalid endDate format: {}", endDate);
            }
        }

        Page<BankTransaction> transactions = bankTransactionRepository.findByTenantIdAndAccountTypeWithFilters(
            tenantId, accType, txType, reconciled, start, end, pageable);
            
        return transactions.map(BankTransactionDto::from);
    }

    private BankTransaction.AccountType parseAccountType(String accountType) {
        if (accountType == null) return BankTransaction.AccountType.MAINTENANCE;
        try {
            return BankTransaction.AccountType.valueOf(accountType.toUpperCase());
        } catch (Exception e) {
            return BankTransaction.AccountType.MAINTENANCE;
        }
    }

    public BankTransactionDto updateTransaction(Long id, BankTransactionDto dto) {
        BankTransaction txn = bankTransactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + id));

        txn.setAmount(dto.getAmount());
        txn.setVendor(dto.getVendor());
        txn.setTxDate(dto.getTxDate());
        txn.setDescription(dto.getDescription());
        if (dto.getType() != null) {
            txn.setType(BankTransaction.TransactionType.valueOf(dto.getType()));
        }
        
        // Re-enrich vendor if changed
        txn.setVendorNormalized(dto.getVendor());
        transactionPatternEnricher.enrichIfMatches(txn);

        return BankTransactionDto.from(bankTransactionRepository.save(txn));
    }

    public SyncStatus getUploadStatus(String tenantId) {
        return uploadStatuses.getOrDefault(tenantId, new SyncStatus());
    }

    public void processStatementAsync(String tenantId, MultipartFile file, String accountType) {
        log.info("Async processing started for file: {} (Account: {})", file.getOriginalFilename(), accountType);
        SyncStatus status = uploadStatuses.computeIfAbsent(tenantId, k -> new SyncStatus());
        if ("RUNNING".equals(status.getStatus())) return;

        status.setStatus("RUNNING");
        status.setStage("INITIALIZING");
        status.setMessage("Preparing statement for persistent storage...");

        // 1. Immediate Persistence & Meta-tracking (Synchronous)
        String fileId = java.util.UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown";
        String lowerName = originalFilename.toLowerCase();
        String extension = lowerName.endsWith(".csv") ? ".csv" : lowerName.endsWith(".xlsx") ? ".xlsx" : ".pdf";
        
        // Path structure: {uploadBaseDir}/{tenantId}/{fileId}{extension}
        String baseDir = uploadBaseDir + "/" + tenantId;
        java.io.File persistentDir = new java.io.File(baseDir);
        if (!persistentDir.exists()) persistentDir.mkdirs();
        
        java.io.File persistentFile = new java.io.File(persistentDir, fileId + extension);
        String fileHash;

        try {
            Files.copy(file.getInputStream(), persistentFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            fileHash = generateFileHash(persistentFile);
            
            // 1a. Refined Idempotency: Skip ONLY if status == COMPLETED AND transactions exist in DB
            var existingCompleted = statementUploadRepository.findByFileHashAndTenantIdAndStatus(fileHash, tenantId, "COMPLETED");
            if (existingCompleted.isPresent()) {
                log.warn("Previous COMPLETED record for hash {} found. Allowing re-process (individual txn dedup handles true duplicates).", fileHash);
                statementUploadRepository.delete(existingCompleted.get());
            }

            
            // 1b. Processing Lock: Check if already PROCESSING
            var existingProcessing = statementUploadRepository.findByFileHashAndTenantIdAndStatus(fileHash, tenantId, "PROCESSING");
            if (existingProcessing.isPresent()) {
                log.warn("Duplicate upload attempt while processing (Hash: {}). Locking request.", fileHash);
                status.setStatus("RUNNING");
                status.setMessage("Statement is already being processed. Please wait.");
                return;
            }

            StatementUpload upload = new StatementUpload();
            upload.setFileId(fileId);
            upload.setTenantId(tenantId);
            upload.setFilePath(persistentFile.getAbsolutePath());
            upload.setFileName(originalFilename);
            upload.setFileHash(fileHash);
            upload.setStatus("UPLOADED");
            upload.setSource("AI");
            upload.setRetryCount(0);
            upload.setAccountType(accountType);
            statementUploadRepository.save(upload);

            // 2. Trigger Async Processing
            processPersistentFileAsync(upload);

        } catch (Exception e) {
            log.error("Failed to persist statement upload", e);
            status.setStatus("ERROR");
            status.setMessage("Upload persistence failed: " + e.getMessage());
        }
    }

    @org.springframework.scheduling.annotation.Async
    void processPersistentFileAsync(StatementUpload upload) {
        String tenantId = upload.getTenantId();
        String fileId = upload.getFileId();
        java.io.File persistentFile = new java.io.File(upload.getFilePath());
        String lowerName = upload.getFileName().toLowerCase();
        String extension = lowerName.endsWith(".csv") ? ".csv" : lowerName.endsWith(".xlsx") ? ".xlsx" : ".pdf";
        String accountType = upload.getAccountType();

        SyncStatus status = uploadStatuses.computeIfAbsent(tenantId, k -> new SyncStatus());
        status.setStatus("RUNNING");

        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                upload.setStatus("PROCESSING");
                statementUploadRepository.save(upload);

                status.setStage("EXTRACTION");
                status.setMessage("Gemini AI is reading the statement...");

                AppConfig config = appConfigService.getConfig();
                String ocrMode = (config != null) ? config.getOcrMode() : "LOCAL";
                List<ParsedBankTransactionDto> parsedTxns;
                
                if ("MODE_MAX_INTELLIGENCE".equals(ocrMode)) {
                    log.info("MAX_INTELLIGENCE Mode enabled: Bypassing heuristics and using Universal AI Parser.");
                    String apiKey = (config != null) ? config.getGeminiApiKey() : null;
                    if (apiKey != null && !apiKey.trim().isEmpty()) {
                        if (extension.equals(".pdf")) {
                            parsedTxns = geminiStatementParserService.parsePdfStatement(persistentFile);
                        } else {
                            parsedTxns = geminiStatementParserService.parseExcelOrCsv(persistentFile, apiKey);
                        }
                    } else {
                        log.warn("MAX_INTELLIGENCE Mode active but Gemini API key is NOT configured. Falling back to local heuristics.");
                        parsedTxns = runLocalHeuristic(extension, persistentFile, tenantId);
                    }
                } else {
                    parsedTxns = runLocalHeuristic(extension, persistentFile, tenantId);
                }

                // Fallback for CSV/XLSX if 0 rows extracted (Only if NOT already in MAX_INTELLIGENCE mode)
                if (parsedTxns.isEmpty() && !extension.equals(".pdf") && !"MODE_MAX_INTELLIGENCE".equals(ocrMode)) {
                    log.info("Heuristic parse for {} returned 0 rows. Checking for Gemini AI Parser fallback...", extension);
                    String apiKey = (config != null) ? config.getGeminiApiKey() : null;
                    if (apiKey != null && !apiKey.trim().isEmpty()) {
                        parsedTxns = geminiStatementParserService.parseExcelOrCsv(persistentFile, apiKey);
                    } else {
                        log.warn("Gemini AI fallback triggered but API key is NOT configured. Skipping AI extraction.");
                    }
                }

                status.setTotalFiles(parsedTxns.size());
                status.setMessage("Extracted " + parsedTxns.size() + " transactions. Persisting...");
                status.setStage("PERSISTENCE");

                int savedCount = persistParsedTransactionsWithStatus(tenantId, parsedTxns, true, status, accountType);

                // Calculate Metrics
                double avgConfidence = parsedTxns.stream()
                        .mapToDouble(t -> t.getConfidenceScore() != null ? t.getConfidenceScore() : 0.0)
                        .average().orElse(0.0);

                if (parsedTxns.isEmpty()) {
                    upload.setStatus("FAILED");
                    upload.setErrorMessage("No transactions extracted (possibly heuristic/AI failure).");
                } else {
                    upload.setStatus("COMPLETED");
                    upload.setErrorMessage(null); // Clear previous errors if re-processing
                }
                upload.setLastProcessedAt(java.time.LocalDateTime.now());
                upload.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                upload.setAvgConfidenceScore(avgConfidence);
                upload.setGeminiCallsCount(calculateGeminiCalls(parsedTxns, extension));
                statementUploadRepository.save(upload);

                status.setStatus("SUCCESS");
                status.setStage("COMPLETED");
                status.setMessage("Successfully processed " + savedCount + " transactions.");
                status.setProcessedFiles(savedCount);

            } catch (Exception e) {
                log.error("Error in async statement processing for fileId: {}", fileId, e);
                RuntimeErrorLogger.log(
                    RuntimeErrorLogger.Module.STATEMENT_UPLOAD,
                    e.getClass().getSimpleName(),
                    e,
                    java.util.Map.of(
                        "fileId", fileId,
                        "fileName", persistentFile.getName(),
                        "tenantId", tenantId,
                        "extension", extension
                    ),
                    "Successful extraction and persistence",
                    e.getMessage() != null ? e.getMessage() : "Unknown error"
                );
                
                int currentRetries = upload.getRetryCount() != null ? upload.getRetryCount() : 0;
                upload.setRetryCount(currentRetries + 1);
                upload.setLastProcessedAt(java.time.LocalDateTime.now());
                
                // Partial Success Check
                boolean someSaved = status.getProcessedFiles() > 0;
                
                if (upload.getRetryCount() >= 3) {
                    upload.setStatus("FAILED_PERMANENT");
                    status.setStage("DEAD_LETTER");
                    status.setMessage("Permanent failure after 3 retries.");
                } else {
                    upload.setStatus(someSaved ? "PARTIAL_SUCCESS" : "FAILED");
                    status.setStatus(someSaved ? "WARNING" : "ERROR");
                    status.setStage(someSaved ? "PARTIAL_SUCCESS" : "FAILED");
                    status.setMessage((someSaved ? "Partial success: " : "Processing failed: ") + e.getMessage());
                }
                
                upload.setErrorMessage(e.getMessage());
                upload.setProcessingTimeMs(System.currentTimeMillis() - startTime);
                statementUploadRepository.save(upload);
            }
        });
    }

    private int calculateGeminiCalls(List<ParsedBankTransactionDto> txns, String extension) {
        if (".csv".equals(extension)) return 0;
        // Approximation: 1 call per ~50 transactions or based on chunking logic in GeminiStatementParserService
        return (int) Math.ceil(txns.size() / 15.0); 
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void cleanupOldUploads() {
        log.info("Starting scheduled cleanup of statement uploads older than 90 days...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(90);
        List<StatementUpload> toDelete = statementUploadRepository.findByCreatedAtBefore(threshold);
        
        for (StatementUpload upload : toDelete) {
            java.io.File file = new java.io.File(upload.getFilePath());
            if (file.exists()) {
                if (file.delete()) {
                    log.info("Deleted old statement file: {}", upload.getFilePath());
                } else {
                    log.warn("Failed to delete file: {}", upload.getFilePath());
                }
            }
        }
        statementUploadRepository.deleteOlderThan(threshold);
        log.info("Cleanup completed.");
    }

    public void reprocessStatement(String fileId) {
        var uploadOpt = statementUploadRepository.findByFileId(fileId);
        if (uploadOpt.isEmpty()) throw new RuntimeException("Upload not found: " + fileId);
        var upload = uploadOpt.get();
        
        if ("PROCESSING".equals(upload.getStatus())) {
            throw new RuntimeException("Statement is already being processed.");
        }
        
        upload.setSource("REPROCESSED");
        processPersistentFileAsync(upload);
    }

    public List<StatementUpload> getRecentUploads(String tenantId) {
        return statementUploadRepository.findByTenantIdOrderByCreatedAtDesc(tenantId); 
    }

    private String generateFileHash(java.io.File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] hash = digest.digest(fileBytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return java.util.UUID.randomUUID().toString();
        }
    }

    public int processPdfStatement(File temp) throws Exception {
        log.info("Processing Bank Statement PDF: {}", temp.getName());
        try {
            List<ParsedBankTransactionDto> parsedTxns = geminiStatementParserService.parsePdfStatement(temp);
            log.info("Parser extracted {} transactions from PDF.", parsedTxns.size());
            return persistParsedTransactionsWithStatus("local_tenant", parsedTxns, true, null, "MAINTENANCE");
        } finally {
            temp.delete();
        }
    }

    public int processCsvStatement(File temp) throws Exception {
        log.info("Processing Bank Statement CSV: {}", temp.getName());
        try {
            List<ParsedBankTransactionDto> parsedTxns = csvStatementParser.parse(temp, "local_tenant");
            log.info("Parser extracted {} transactions from CSV.", parsedTxns.size());
            return persistParsedTransactionsWithStatus("local_tenant", parsedTxns, false, null, "MAINTENANCE");
        } finally {
            temp.delete();
        }
    }

    private int persistParsedTransactionsWithStatus(String tenantId, List<ParsedBankTransactionDto> parsedTxns, boolean useSha256, SyncStatus status, String accountType) {
        if (parsedTxns == null || parsedTxns.isEmpty()) {
            if (status != null) {
                status.setStatus("COMPLETED");
                status.setStage("PERSISTENCE");
                status.setMessage("No transactions found to persist.");
                status.setProcessedFiles(0);
            }
            return 0;
        }

        int totalToProcess = parsedTxns.size();
        List<BankTransaction> toSave = new ArrayList<>();
        int current = 0;
        
        BankTransaction.AccountType accType;
        try {
            accType = BankTransaction.AccountType.valueOf(accountType.toUpperCase());
        } catch (Exception e) {
            log.warn("Invalid accountType '{}', defaulting to MAINTENANCE", accountType);
            accType = BankTransaction.AccountType.MAINTENANCE;
        }

        for (ParsedBankTransactionDto dto : parsedTxns) {
            current++;
            if (status != null) {
                status.setProcessedFiles(current);
                status.setMessage("Saving transaction " + current + " of " + totalToProcess);
            }
            
            if (dto.getTxDate() == null || dto.getAmount() == null || dto.getDescription() == null) {
                log.warn("Skipping invalid transaction: {}", dto.getDescription());
                continue;
            }

            LocalDate txDate = csvStatementParser.parseDateRobustly(dto.getTxDate());
            if (txDate == null) {
                log.warn("Skipping transaction due to unparseable date: {}", dto.getTxDate());
                continue;
            }

            // Simple deduplication check
            String rawRef = txDate + "|" + dto.getAmount() + "|" + dto.getDescription();
            String refHash = generateHash(rawRef);
            if (bankTransactionRepository.existsByReferenceNumberAndTenantId(refHash, tenantId)) {
                log.info("Skipping duplicate transaction: {}", dto.getDescription());
                continue;
            }

            BankTransaction txn = new BankTransaction();
            txn.setTxDate(txDate);
            txn.setDescription(dto.getDescription());
            txn.setVendor(dto.getVendor() != null ? dto.getVendor() : "Unknown");
            txn.setAmount(dto.getAmount());
            txn.setTenantId(tenantId); 
            txn.setReferenceNumber(refHash);

            try {
                txn.setType(BankTransaction.TransactionType.valueOf(dto.getType().toUpperCase()));
            } catch (Exception e) {
                txn.setType(BankTransaction.TransactionType.DEBIT);
            }

            String categoryName = categorizationService.categorize(
                txn.getVendor(),
                txn.getDescription(), 
                dto.getCategory(), 
                txn.getType().name()
            );
            Category category = categorizationService.getOrCreateCategoryEntity(categoryName, dto.getType());
            txn.setCategory(category);
            txn.setVendorNormalized(dto.getVendor()); 
            txn.setAccountType(accType);
            
            // Map AI Metadata
            double confidence = dto.getConfidenceScore() != null ? dto.getConfidenceScore() : 0.0;
            txn.setConfidenceScore(confidence);
            txn.setAiReasoning(dto.getAiReasoning());
            txn.setOriginalSnippet(dto.getOriginalSnippet());
            
            // Status Automation
            if (confidence >= 0.90) {
                txn.setStatus("AUTO_VALIDATED");
            } else if (confidence > 0) {
                txn.setStatus("LOW_CONFIDENCE");
            } else {
                txn.setStatus("NEEDS_REVIEW");
            }
            
            transactionPatternEnricher.enrichIfMatches(txn);
            
            // Populate/Update Vendor statistics for the "Vendor Intel" page (Only for Spending/Debit)
            if (txn.getType() == BankTransaction.TransactionType.DEBIT) {
                vendorManager.updateVendorStats(tenantId, txn.getVendor(), txn.getAmount(), txn.getTxDate());
            }

            toSave.add(txn);
        }

        bankTransactionRepository.saveAll(toSave);
        return toSave.size();
    }

    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 32);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
    
    private List<ParsedBankTransactionDto> runLocalHeuristic(String extension, File persistentFile, String tenantId) throws Exception {
        if (extension.equals(".csv")) {
            return csvStatementParser.parse(persistentFile, tenantId);
        } else if (extension.equals(".xlsx")) {
            return xlsxStatementParser.parse(persistentFile, tenantId);
        } else {
            return geminiStatementParserService.parsePdfStatement(persistentFile);
        }
    }
}
