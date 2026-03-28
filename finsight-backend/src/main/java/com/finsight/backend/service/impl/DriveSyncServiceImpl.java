package com.finsight.backend.service.impl;

import com.finsight.backend.client.GoogleDriveClient;
import com.finsight.backend.dto.SyncStatus;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.entity.ReceiptSyncRun;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.repository.ReceiptSyncRunRepository;
import com.finsight.backend.service.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class DriveSyncServiceImpl implements DriveSyncService {
    private static final Logger log = LoggerFactory.getLogger(DriveSyncServiceImpl.class);

    private final AppConfigService appConfigService;
    private final GoogleDriveClient driveClient;
    private final ReceiptRepository receiptRepository;
    private final OcrService ocrService;
    private final ClassificationService classificationService;
    private final VendorManager vendorManager;
    private final ReceiptSyncRunRepository receiptSyncRunRepository;

    public DriveSyncServiceImpl(AppConfigService appConfigService, 
                                GoogleDriveClient driveClient, 
                                ReceiptRepository receiptRepository,
                                OcrService ocrService,
                                ClassificationService classificationService,
                                VendorManager vendorManager,
                                ReceiptSyncRunRepository receiptSyncRunRepository) {
        this.appConfigService = appConfigService;
        this.driveClient = driveClient;
        this.receiptRepository = receiptRepository;
        this.ocrService = ocrService;
        this.classificationService = classificationService;
        this.vendorManager = vendorManager;
        this.receiptSyncRunRepository = receiptSyncRunRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SyncStatus getStatus(String tenantId) {
        Optional<ReceiptSyncRun> runOpt = receiptSyncRunRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId);
        
        SyncStatus status = new SyncStatus();
        if (runOpt.isPresent()) {
            ReceiptSyncRun run = runOpt.get();
            status.setStatus(run.getStatus());
            status.setStage(run.getStage());
            status.setProcessedFiles(run.getProcessedFiles());
            status.setFailedFiles(run.getFailedFiles());
            status.setSkippedFiles(run.getSkippedFiles());
            status.setTotalFiles(run.getTotalFiles());
            status.setLastSyncAt(run.getCompletedAt());
            status.setMessage(run.getErrorMessage());
        } else {
            // Check config for legacy sync timestamp
            try {
                AppConfig config = appConfigService.getConfig();
                status.setLastSyncAt(config.getSyncedAt());
            } catch (Exception e) {
                log.warn("Could not fetch lastSyncAt: {}", e.getMessage());
            }
        }
        return status;
    }

    @Override
    public void sync(String tenantId) {
        // 1. Concurrency Check
        Optional<ReceiptSyncRun> lastRun = receiptSyncRunRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId);
        if (lastRun.isPresent() && "RUNNING".equals(lastRun.get().getStatus())) {
            log.warn("Sync already running for tenant: {}", tenantId);
            return;
        }

        // 2. Initialize Run Tracking
        ReceiptSyncRun run = new ReceiptSyncRun();
        run.setTenantId(tenantId);
        run.setStatus("RUNNING");
        run.setStage("INITIALIZING");
        run = receiptSyncRunRepository.save(run);
        
        final Long runId = run.getId();

        CompletableFuture.runAsync(() -> {
            try {
                AppConfig config = appConfigService.getConfig();
                String folderId = extractFolderId(config.getDriveFolderUrl());
                if (folderId == null) {
                    updateRunStatus(runId, "ERROR", "COMPLETED", "Invalid Drive Folder URL.");
                    return;
                }

                Drive driveService = driveClient.getDriveService(config.getServiceAccountJson());
                updateRunStage(runId, "SCANNING");
                
                List<File> files = driveClient.listFilesRecursively(driveService, folderId);
                updateRunCounts(runId, files.size(), 0, 0, 0, "PROCESS_OCR");

                int processed = 0, skipped = 0, failed = 0;
                for (File file : files) {
                    try {
                        if (receiptRepository.findByDriveFileId(file.getId()).isPresent() || 
                            (file.getMd5Checksum() != null && receiptRepository.findByContentHash(file.getMd5Checksum()).isPresent())) {
                            skipped++;
                            updateRunCounts(runId, files.size(), processed, failed, skipped, "PROCESS_OCR");
                            continue;
                        }

                        byte[] content = driveClient.downloadFile(driveService, file.getId());
                        java.util.Map<String, Object> extraction = ocrService.extractData(new java.io.ByteArrayInputStream(content), file.getName(), config.getOcrMode());

                        Receipt receipt = new Receipt();
                        receipt.setTenantId(tenantId);
                        receipt.setDriveFileId(file.getId());
                        receipt.setFileName(file.getName());
                        receipt.setGoogleDriveLink(file.getWebViewLink());
                        receipt.setContentHash(file.getMd5Checksum());
                        receipt.setOcrConfidence((Double) extraction.getOrDefault("confidence", 0.1));
                        receipt.setOcrModeUsed(config.getOcrMode());
                        receipt.setStatus("PROCESSED");

                        receipt.setVendor((String) extraction.getOrDefault("vendor", "Unknown"));
                        Object amountObj = extraction.get("amount");
                        receipt.setAmount(amountObj instanceof Number ? BigDecimal.valueOf(((Number) amountObj).doubleValue()) : BigDecimal.ZERO);
                        
                        String dateStr = (String) extraction.get("date");
                        try {
                            receipt.setDate(dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now());
                        } catch (Exception e) {
                            receipt.setDate(LocalDate.now());
                        }

                        try {
                            String rawText = (String) extraction.getOrDefault("raw_text", "");
                            receipt.setCategory(classificationService.classify(rawText, receipt.getVendor()));
                        } catch (Exception e) {
                            receipt.setCategory("Uncategorized");
                        }

                        receiptRepository.save(receipt);
                        vendorManager.updateVendorStats(tenantId, receipt.getVendor(), receipt.getAmount(), receipt.getDate());

                        processed++;
                        updateRunCounts(runId, files.size(), processed, failed, skipped, "PROCESS_OCR");
                    } catch (Exception e) {
                        failed++;
                        updateRunCounts(runId, files.size(), processed, failed, skipped, "PROCESS_OCR");
                        log.error("Failed to process file: {}", file.getName(), e);
                    }
                }

                updateRunStatus(runId, "SUCCESS", "COMPLETED", null);
                
                // Update config
                config.setSyncedAt(LocalDateTime.now());
                appConfigService.saveConfig(config);

            } catch (Exception e) {
                updateRunStatus(runId, "ERROR", "COMPLETED", "Sync failed: " + e.getMessage());
                log.error("Sync failed", e);
            }
        });
    }

    private void updateRunStage(Long runId, String stage) {
        receiptSyncRunRepository.findById(runId).ifPresent(r -> {
            r.setStage(stage);
            receiptSyncRunRepository.save(r);
        });
    }

    private void updateRunCounts(Long runId, int total, int processed, int failed, int skipped, String stage) {
        receiptSyncRunRepository.findById(runId).ifPresent(r -> {
            r.setTotalFiles(total);
            r.setProcessedFiles(processed);
            r.setFailedFiles(failed);
            r.setSkippedFiles(skipped);
            r.setStage(stage);
            receiptSyncRunRepository.save(r);
        });
    }

    private void updateRunStatus(Long runId, String status, String stage, String error) {
        receiptSyncRunRepository.findById(runId).ifPresent(r -> {
            r.setStatus(status);
            r.setStage(stage);
            r.setErrorMessage(error);
            if (!"RUNNING".equals(status)) {
                r.setCompletedAt(LocalDateTime.now());
            }
            receiptSyncRunRepository.save(r);
        });
    }

    private String extractFolderId(String url) {
        if (url == null || !url.contains("folders/")) return null;
        String[] parts = url.split("folders/");
        if (parts.length < 2) return null;
        return parts[1].split("\\?")[0];
    }
}
