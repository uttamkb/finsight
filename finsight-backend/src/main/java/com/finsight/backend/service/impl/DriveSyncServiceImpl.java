package com.finsight.backend.service.impl;

import com.finsight.backend.client.GoogleDriveClient;
import com.finsight.backend.dto.SyncStatus;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.service.AppConfigService;
import com.finsight.backend.service.ClassificationService;
import com.finsight.backend.service.DriveSyncService;
import com.finsight.backend.service.OcrService;
import com.finsight.backend.service.VendorManager;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DriveSyncServiceImpl implements DriveSyncService {
    private static final Logger log = LoggerFactory.getLogger(DriveSyncServiceImpl.class);

    private final AppConfigService appConfigService;
    private final GoogleDriveClient driveClient;
    private final ReceiptRepository receiptRepository;
    private final OcrService ocrService;
    private final ClassificationService classificationService;
    private final VendorManager vendorManager;
    private final Map<String, SyncStatus> statuses = new ConcurrentHashMap<>();

    public DriveSyncServiceImpl(AppConfigService appConfigService, 
                                GoogleDriveClient driveClient, 
                                ReceiptRepository receiptRepository,
                                OcrService ocrService,
                                ClassificationService classificationService,
                                VendorManager vendorManager) {
        this.appConfigService = appConfigService;
        this.driveClient = driveClient;
        this.receiptRepository = receiptRepository;
        this.ocrService = ocrService;
        this.classificationService = classificationService;
        this.vendorManager = vendorManager;
    }

    @Override
    public SyncStatus getStatus(String tenantId) {
        SyncStatus status = statuses.getOrDefault(tenantId, new SyncStatus());
        if (status.getLastSyncAt() == null) {
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
        SyncStatus status = statuses.computeIfAbsent(tenantId, k -> new SyncStatus());
        if ("RUNNING".equals(status.getStatus())) return;

        status.setStatus("RUNNING");
        status.setStage("INITIALIZING");
        status.setProcessedFiles(0);
        status.setFailedFiles(0);
        status.setSkippedFiles(0);
        status.getLogs().clear();
        status.addLog("INFO: Sync sequence initiated.");

        CompletableFuture.runAsync(() -> {
            try {
                AppConfig config = appConfigService.getConfig();
                String folderId = extractFolderId(config.getDriveFolderUrl());
                if (folderId == null) {
                    status.setStatus("ERROR");
                    status.setMessage("Invalid Drive Folder URL.");
                    return;
                }

                Drive driveService = driveClient.getDriveService(config.getServiceAccountJson());
                status.setStage("SCANNING");
                List<File> files = driveClient.listFilesRecursively(driveService, folderId);
                
                status.setTotalFiles(files.size());
                status.setStage("PROCESS_OCR");

                int processed = 0, skipped = 0, failed = 0;
                for (File file : files) {
                    try {
                        if (receiptRepository.findByDriveFileId(file.getId()).isPresent() || 
                            (file.getMd5Checksum() != null && receiptRepository.findByContentHash(file.getMd5Checksum()).isPresent())) {
                            skipped++;
                            status.setSkippedFiles(skipped);
                            continue;
                        }

                        status.setMessage("Processing: " + file.getName());
                        byte[] content = driveClient.downloadFile(driveService, file.getId());
                        Map<String, Object> extraction = ocrService.extractData(new ByteArrayInputStream(content), file.getName(), config.getOcrMode());

                        Receipt receipt = new Receipt();
                        receipt.setTenantId(tenantId);
                        receipt.setDriveFileId(file.getId());
                        receipt.setFileName(file.getName());
                        receipt.setGoogleDriveLink(file.getWebViewLink());
                        receipt.setContentHash(file.getMd5Checksum());
                        receipt.setOcrConfidence((Double) extraction.getOrDefault("confidence", 0.1));
                        receipt.setOcrModeUsed(config.getOcrMode());
                        receipt.setStatus("PROCESSED");

                        // Map extracted fields
                        receipt.setVendor((String) extraction.getOrDefault("vendor", "Unknown"));
                        Object amountObj = extraction.get("amount");
                        receipt.setAmount(amountObj instanceof Number ? BigDecimal.valueOf(((Number) amountObj).doubleValue()) : BigDecimal.ZERO);
                        
                        String dateStr = (String) extraction.get("date");
                        try {
                            receipt.setDate(dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now());
                        } catch (Exception e) {
                            receipt.setDate(LocalDate.now());
                        }

                        // Classification
                        try {
                            String rawText = (String) extraction.getOrDefault("raw_text", "");
                            receipt.setCategory(classificationService.classify(rawText, receipt.getVendor()));
                        } catch (Exception e) {
                            receipt.setCategory("Uncategorized");
                        }

                        receiptRepository.save(receipt);

                        // Update Vendor stats
                        vendorManager.updateVendorStats(tenantId, receipt.getVendor(), receipt.getAmount(), receipt.getDate());

                        processed++;
                        status.setProcessedFiles(processed);
                        status.addLog("SUCCESS: " + file.getName());
                    } catch (Exception e) {
                        failed++;
                        status.setFailedFiles(failed);
                        status.addLog("ERROR: " + file.getName() + " - " + e.getMessage());
                    }
                }

                status.setStatus("SUCCESS");
                status.setStage("COMPLETED");
                LocalDateTime now = LocalDateTime.now();
                config.setSyncedAt(now);
                appConfigService.saveConfig(config);
                status.setLastSyncAt(now);

            } catch (Exception e) {
                status.setStatus("ERROR");
                status.setMessage("Sync failed: " + e.getMessage());
                log.error("Sync failed", e);
            }
        });
    }

    private String extractFolderId(String url) {
        if (url == null || !url.contains("folders/")) return null;
        String[] parts = url.split("folders/");
        if (parts.length < 2) return null;
        return parts[1].split("\\?")[0];
    }
}
