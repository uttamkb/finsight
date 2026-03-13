package com.finsight.backend.repository;

import com.finsight.backend.entity.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditTrailRepository extends JpaRepository<AuditTrail, Long> {

    // Paginated: used by old controllers
    Page<AuditTrail> findByTenantIdAndResolvedOrderByCreatedAtDesc(String tenantId, Boolean resolved, Pageable pageable);

    // Count helpers
    long countByTenantIdAndResolved(String tenantId, Boolean resolved);

    // Non-paginated list: used by ReconciliationController
    @EntityGraph(attributePaths = {"transaction", "transaction.category", "transaction.receipt", "receipt"})
    List<AuditTrail> findByTenantId(String tenantId);

    @EntityGraph(attributePaths = {"transaction", "transaction.category", "transaction.receipt", "receipt"})
    List<AuditTrail> findByTenantIdAndIssueType(String tenantId, AuditTrail.IssueType issueType);
}
