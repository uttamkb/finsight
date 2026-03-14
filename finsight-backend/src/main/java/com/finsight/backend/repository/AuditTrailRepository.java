package com.finsight.backend.repository;

import com.finsight.backend.entity.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {

    // Paginated: used by old controllers
    Page<AuditTrail> findByTenantIdAndResolvedOrderByCreatedAtDesc(String tenantId, Boolean resolved, Pageable pageable);

    // Count helpers (H3 — replaces full table fetch for stats)
    long countByTenantIdAndResolved(String tenantId, Boolean resolved);

    // Non-paginated list: used by ReconciliationController
    @EntityGraph(attributePaths = {"transaction", "transaction.category", "transaction.receipt", "receipt"})
    List<AuditTrail> findByTenantId(String tenantId);

    @EntityGraph(attributePaths = {"transaction", "transaction.category", "transaction.receipt", "receipt"})
    List<AuditTrail> findByTenantIdAndIssueType(String tenantId, AuditTrail.IssueType issueType);

    // H1 — idempotency: prevents duplicate audit entries on repeated reconciliation runs
    boolean existsByTransactionIdAndIssueTypeAndResolvedFalse(Long transactionId, AuditTrail.IssueType issueType);

    // H4 — targeted audit resolution in manuallyLink() (replaces full table scan)
    @Query("SELECT a FROM AuditTrail a WHERE a.resolved = false " +
           "AND (a.transaction.id = :txnId OR a.receipt.id = :receiptId)")
    List<AuditTrail> findUnresolvedByTxnOrReceipt(@Param("txnId") Long txnId, @Param("receiptId") Long receiptId);
}
