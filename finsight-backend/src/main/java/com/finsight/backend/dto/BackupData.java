package com.finsight.backend.dto;

import com.finsight.backend.entity.*;
import java.util.List;

public class BackupData {
    private List<Receipt> receipts;
    private List<BankTransaction> transactions;
    private List<Vendor> vendors;
    private List<Category> categories;
    private List<AuditTrail> auditTrails;
    private List<ForensicAnomaly> anomalies;
    private List<AppConfig> configs;

    // Getters and Setters
    public List<Receipt> getReceipts() { return receipts; }
    public void setReceipts(List<Receipt> receipts) { this.receipts = receipts; }

    public List<BankTransaction> getTransactions() { return transactions; }
    public void setTransactions(List<BankTransaction> transactions) { this.transactions = transactions; }

    public List<Vendor> getVendors() { return vendors; }
    public void setVendors(List<Vendor> vendors) { this.vendors = vendors; }

    public List<Category> getCategories() { return categories; }
    public void setCategories(List<Category> categories) { this.categories = categories; }

    public List<AuditTrail> getAuditTrails() { return auditTrails; }
    public void setAuditTrails(List<AuditTrail> auditTrails) { this.auditTrails = auditTrails; }

    public List<ForensicAnomaly> getAnomalies() { return anomalies; }
    public void setAnomalies(List<ForensicAnomaly> anomalies) { this.anomalies = anomalies; }

    public List<AppConfig> getConfigs() { return configs; }
    public void setConfigs(List<AppConfig> configs) { this.configs = configs; }
}
