package com.finsight.backend.service;

import com.finsight.backend.dto.CategoryInsightDto;
import com.finsight.backend.dto.VendorInsightDto;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.VendorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public List<CategoryInsightDto> getSpendingByCategory(String tenantId) {
        return bankTransactionRepository.getTopSpendingByCategory(tenantId, BankTransaction.AccountType.MAINTENANCE);
    }

    public java.util.Map<String, Long> getOcrModeStats() {
        List<Object[]> stats = receiptRepository.getOcrModeStats("local_tenant");
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        for (Object[] row : stats) {
            result.put((String) row[0], (Long) row[1]);
        }
        return result;
    }

    public Map<String, Object> getCategoryDrilldown(String categoryName, String tenantId) {
        Map<String, Object> result = new HashMap<>();
        
        // Use MAINTENANCE as default account type for insights
        BankTransaction.AccountType accountType = BankTransaction.AccountType.MAINTENANCE;
        
        // 1. Top vendors in this category
        List<VendorInsightDto> topVendors = bankTransactionRepository.getTopSpendingByCategoryAndVendor(
                tenantId, categoryName, accountType, PageRequest.of(0, 5)
        );
        
        // 2. Recent transactions in this category
        List<BankTransaction> recentTransactions = bankTransactionRepository.findRecentByCategory(
                tenantId, categoryName, accountType, PageRequest.of(0, 5)
        );
        
        result.put("topVendors", topVendors);
        result.put("recentTransactions", recentTransactions);
        result.put("categoryName", categoryName);
        
        return result;
    }
}
