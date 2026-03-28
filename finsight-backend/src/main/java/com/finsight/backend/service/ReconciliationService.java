package com.finsight.backend.service;

import com.finsight.backend.dto.ReconciliationResultDto;
import com.finsight.backend.dto.ReconciliationStatsDto;

public interface ReconciliationService {
    ReconciliationResultDto runReconciliation(String tenantId, String accountType);
    void manuallyLink(Long bankTransactionId, Long receiptId);
    void markAsNoReceiptNeeded(Long bankTransactionId);
    ReconciliationStatsDto getAuditStatistics(String tenantId);
    java.util.List<com.finsight.backend.entity.AuditTrail> getAuditTrail(String tenantId, String issueType);
    void resolveAnomaly(Long id);
}
