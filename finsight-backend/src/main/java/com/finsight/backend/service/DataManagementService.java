package com.finsight.backend.service;

import com.finsight.backend.dto.BackupData;
import com.finsight.backend.entity.*;
import com.finsight.backend.repository.*;
import com.finsight.backend.repository.survey.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataManagementService {

    private final ReceiptRepository receiptRepository;
    private final BankTransactionRepository bankTransactionRepository;
    private final VendorRepository vendorRepository;
    private final CategoryRepository categoryRepository;
    private final AuditTrailRepository auditTrailRepository;
    private final ForensicAnomalyRepository forensicAnomalyRepository;
    private final AppConfigRepository appConfigRepository;
    private final ReceiptSyncRunRepository receiptSyncRunRepository;
    private final VendorAliasRepository vendorAliasRepository;
    private final ReconciliationRunRepository reconciliationRunRepository;
    private final CategoryKeywordMappingRepository categoryKeywordMappingRepository;
    private final StatementUploadRepository statementUploadRepository;
    private final VendorDictionaryRepository vendorDictionaryRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyInsightRepository surveyInsightRepository;
    private final SurveyActionItemRepository surveyActionItemRepository;
    private final SurveyResponseRepository surveyResponseRepository;
    private final ParserPatternRepository parserPatternRepository;

    public DataManagementService(
            ReceiptRepository receiptRepository,
            BankTransactionRepository bankTransactionRepository,
            VendorRepository vendorRepository,
            CategoryRepository categoryRepository,
            AuditTrailRepository auditTrailRepository,
            ForensicAnomalyRepository forensicAnomalyRepository,
            AppConfigRepository appConfigRepository,
            ReceiptSyncRunRepository receiptSyncRunRepository,
            VendorAliasRepository vendorAliasRepository,
            ReconciliationRunRepository reconciliationRunRepository,
            CategoryKeywordMappingRepository categoryKeywordMappingRepository,
            StatementUploadRepository statementUploadRepository,
            VendorDictionaryRepository vendorDictionaryRepository,
            SurveyRepository surveyRepository,
            SurveyInsightRepository surveyInsightRepository,
            SurveyActionItemRepository surveyActionItemRepository,
            SurveyResponseRepository surveyResponseRepository,
            ParserPatternRepository parserPatternRepository) {
        this.receiptRepository = receiptRepository;
        this.bankTransactionRepository = bankTransactionRepository;
        this.vendorRepository = vendorRepository;
        this.categoryRepository = categoryRepository;
        this.auditTrailRepository = auditTrailRepository;
        this.forensicAnomalyRepository = forensicAnomalyRepository;
        this.appConfigRepository = appConfigRepository;
        this.receiptSyncRunRepository = receiptSyncRunRepository;
        this.vendorAliasRepository = vendorAliasRepository;
        this.reconciliationRunRepository = reconciliationRunRepository;
        this.categoryKeywordMappingRepository = categoryKeywordMappingRepository;
        this.statementUploadRepository = statementUploadRepository;
        this.vendorDictionaryRepository = vendorDictionaryRepository;
        this.surveyRepository = surveyRepository;
        this.surveyInsightRepository = surveyInsightRepository;
        this.surveyActionItemRepository = surveyActionItemRepository;
        this.surveyResponseRepository = surveyResponseRepository;
        this.parserPatternRepository = parserPatternRepository;
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
        // 1. Clear existing data in correct order
        clearTransactionalAndLearnedData();
        
        // For import, we do overwrite config as it's a full restore
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
        clearTransactionalAndLearnedData();
        // appConfigRepository.deleteAllInBatch(); // PRESERVED: Do not clear system settings
    }

    /**
     * Helper to clear all transactional, run, survey, and learned data tables.
     * Preserves AppConfig.
     */
    private void clearTransactionalAndLearnedData() {
        // Clear Survey dependencies first
        surveyActionItemRepository.deleteAllInBatch();
        surveyInsightRepository.deleteAllInBatch();
        surveyResponseRepository.deleteAllInBatch();
        surveyRepository.deleteAllInBatch();

        // Clear Audit and Runs
        auditTrailRepository.deleteAllInBatch();
        reconciliationRunRepository.deleteAllInBatch();
        receiptSyncRunRepository.deleteAllInBatch();
        statementUploadRepository.deleteAllInBatch();

        // Clear Transactional and Learned data
        bankTransactionRepository.deleteAllInBatch();
        receiptRepository.deleteAllInBatch();
        forensicAnomalyRepository.deleteAllInBatch();
        
        // Clear Knowledge Base
        vendorAliasRepository.deleteAllInBatch();
        vendorDictionaryRepository.deleteAllInBatch();
        categoryKeywordMappingRepository.deleteAllInBatch();
        parserPatternRepository.deleteAllInBatch();
        
        // Base data
        vendorRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }
}

