package com.finsight.backend.service.impl;

import com.finsight.backend.entity.Receipt;
import com.finsight.backend.exception.ResourceNotFoundException;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.service.ReceiptService;
import com.finsight.backend.client.GoogleDriveClient;
import com.finsight.backend.service.TrainingDataService;
import com.finsight.backend.service.AppConfigService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final GoogleDriveClient driveClient;
    private final TrainingDataService trainingDataService;
    private final AppConfigService appConfigService;

    public ReceiptServiceImpl(ReceiptRepository receiptRepository,
                              GoogleDriveClient driveClient,
                              TrainingDataService trainingDataService,
                              AppConfigService appConfigService) {
        this.receiptRepository = receiptRepository;
        this.driveClient = driveClient;
        this.trainingDataService = trainingDataService;
        this.appConfigService = appConfigService;
    }

    @Override
    public Page<Receipt> getAllReceipts(String tenantId, String search, Pageable pageable) {
        if (search != null && !search.trim().isEmpty()) {
            return receiptRepository.findByTenantIdAndVendorContainingIgnoreCaseOrTenantIdAndFileNameContainingIgnoreCase(tenantId, search, tenantId, search, pageable);
        }
        return receiptRepository.findByTenantId(tenantId, pageable);
    }

    @Override
    public Receipt getReceiptById(Long id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt not found with id: " + id));
    }

    @Override
    public Receipt saveReceipt(Receipt receipt) {
        return receiptRepository.save(receipt);
    }

    @Override
    public Receipt updateReceipt(Long id, Receipt updateData) {
        Receipt existing = getReceiptById(id);
        
        // Update fields
        existing.setVendor(updateData.getVendor());
        existing.setAmount(updateData.getAmount());
        existing.setDate(updateData.getDate());
        existing.setCategory(updateData.getCategory());
        existing.setStatus("VERIFIED"); // Mark as manually verified

        Receipt saved = receiptRepository.save(existing);
        
        // Trigger Harvesting in background thread to avoid slowing down UI
        new Thread(() -> {
            try {
                var config = appConfigService.getConfig();
                var driveService = driveClient.getDriveService(config.getServiceAccountJson());
                byte[] content = driveClient.downloadFile(driveService, saved.getDriveFileId());
                trainingDataService.harvest(saved, content);
            } catch (Exception e) {
                // Log and continue, don't fail the update
            }
        }).start();

        return saved;
    }
}
