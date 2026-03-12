package com.finsight.backend.service.impl;

import com.finsight.backend.entity.Receipt;
import com.finsight.backend.exception.ResourceNotFoundException;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.service.ReceiptService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;

    public ReceiptServiceImpl(ReceiptRepository receiptRepository) {
        this.receiptRepository = receiptRepository;
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
}
