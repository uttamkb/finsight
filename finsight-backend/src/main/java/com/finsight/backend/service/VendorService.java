package com.finsight.backend.service;

import com.finsight.backend.dto.CategoryInsightDto;
import com.finsight.backend.dto.VendorInsightDto;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.VendorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VendorService {

    private final BankTransactionRepository bankTransactionRepository;
    private final com.finsight.backend.repository.ReceiptRepository receiptRepository;
    private final VendorRepository vendorRepository;

    public VendorService(BankTransactionRepository bankTransactionRepository,
                         com.finsight.backend.repository.ReceiptRepository receiptRepository,
                         VendorRepository vendorRepository) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.receiptRepository = receiptRepository;
        this.vendorRepository = vendorRepository;
    }

    public List<VendorInsightDto> getTopVendors(int limit) {
        return vendorRepository.findByTenantIdOrderByTotalSpentDesc("local_tenant")
                .stream()
                .limit(limit)
                .map(v -> new VendorInsightDto(v.getName(), v.getTotalSpent(), (long) v.getTotalPayments()))
                .toList();
    }

    public List<CategoryInsightDto> getSpendByCategory() {
        return bankTransactionRepository.getTopSpendingByCategory("local_tenant");
    }

    public java.util.Map<String, Long> getOcrModeStats() {
        List<Object[]> stats = receiptRepository.getOcrModeStats("local_tenant");
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        for (Object[] row : stats) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }
}
