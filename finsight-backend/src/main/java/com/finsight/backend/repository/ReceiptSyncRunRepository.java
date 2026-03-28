package com.finsight.backend.repository;

import com.finsight.backend.entity.ReceiptSyncRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ReceiptSyncRunRepository extends JpaRepository<ReceiptSyncRun, Long> {
    Optional<ReceiptSyncRun> findFirstByTenantIdOrderByStartedAtDesc(String tenantId);
}
