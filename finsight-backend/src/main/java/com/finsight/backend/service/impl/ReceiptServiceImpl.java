package com.finsight.backend.service.impl;

import com.finsight.backend.entity.Receipt;
import com.finsight.backend.exception.ResourceNotFoundException;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.service.ReceiptService;
import com.finsight.backend.service.TrainingDataService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final TrainingDataService trainingDataService;

    public ReceiptServiceImpl(ReceiptRepository receiptRepository,
                              TrainingDataService trainingDataService) {
        this.receiptRepository = receiptRepository;
        this.trainingDataService = trainingDataService;
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
        
        // Cleanly offload to async service for AI harvesting
        trainingDataService.harvestAsync(saved);

        return saved;
    }
}
