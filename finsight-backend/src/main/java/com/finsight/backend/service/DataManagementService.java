package com.finsight.backend.service;

import com.finsight.backend.dto.BackupData;
import com.finsight.backend.entity.*;
import com.finsight.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DataManagementService {

    private final ReceiptRepository receiptRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final ForensicAnomalyRepository forensicAnomalyRepository;
    private final AppConfigRepository appConfigRepository;

    public DataManagementService(
            ReceiptRepository receiptRepository,
            BankTransactionRepository bankTransactionRepository,
            VendorRepository vendorRepository,
            CategoryRepository categoryRepository,
            AuditTrailRepository auditTrailRepository,
            ForensicAnomalyRepository forensicAnomalyRepository,
            AppConfigRepository appConfigRepository) {
        this.receiptRepository = receiptRepository;
        this.bankTransactionRepository = bankTransactionRepository;
        this.vendorRepository = vendorRepository;
        this.categoryRepository = categoryRepository;
        this.auditTrailRepository = auditTrailRepository;
        this.forensicAnomalyRepository = forensicAnomalyRepository;
        this.appConfigRepository = appConfigRepository;
    }

    public BackupData exportData() {
        BackupData backup = new BackupData();
        backup.setReceipts(receiptRepository.findAll());
        backup.setTransactions(bankTransactionRepository.findAll());
        backup.setVendors(vendorRepository.findAll());
        backup.setCategories(categoryRepository.findAll());
        backup.setAuditTrails(auditTrailRepository.findAll());
        backup.setAnomalies(forensicAnomalyRepository.findAll());
        backup.setConfigs(appConfigRepository.findAll());
        return backup;
    }

    @Transactional
    public void importData(BackupData data) {
        // 1. Clear existing data in correct order (AuditTrail/Transactions have FKs)
        auditTrailRepository.deleteAllInBatch();
        bankTransactionRepository.deleteAllInBatch();
        receiptRepository.deleteAllInBatch();
        vendorRepository.deleteAllInBatch();
        forensicAnomalyRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        // We might want to keep the config or overwrite it? 
        // User asked for "full data backup", so overwrite.
        appConfigRepository.deleteAllInBatch();

        // 2. Restore data
        if (data.getCategories() != null) categoryRepository.saveAll(data.getCategories());
        if (data.getVendors() != null) vendorRepository.saveAll(data.getVendors());
        if (data.getReceipts() != null) receiptRepository.saveAll(data.getReceipts());
        if (data.getTransactions() != null) bankTransactionRepository.saveAll(data.getTransactions());
        if (data.getAuditTrails() != null) auditTrailRepository.saveAll(data.getAuditTrails());
        if (data.getAnomalies() != null) forensicAnomalyRepository.saveAll(data.getAnomalies());
        if (data.getConfigs() != null) appConfigRepository.saveAll(data.getConfigs());
    }

    @Transactional
    public void resetDatabase() {
        auditTrailRepository.deleteAllInBatch();
        bankTransactionRepository.deleteAllInBatch();
        receiptRepository.deleteAllInBatch();
        vendorRepository.deleteAllInBatch();
        forensicAnomalyRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        appConfigRepository.deleteAllInBatch();
    }
}
