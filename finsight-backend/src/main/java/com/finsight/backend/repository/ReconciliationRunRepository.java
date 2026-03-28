package com.finsight.backend.repository;

import com.finsight.backend.entity.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {
    List<ReconciliationRun> findByTenantIdOrderByStartedAtDesc(String tenantId);
    Optional<ReconciliationRun> findFirstByTenantIdAndAccountTypeAndStatus(String tenantId, String accountType, String status);
}
