package com.finsight.backend.repository;

import com.finsight.backend.entity.Receipt;
import com.finsight.backend.entity.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    Optional<Receipt> findByDriveFileId(String driveFileId);
    Optional<Receipt> findByContentHash(String contentHash);
    Page<Receipt> findByTenantId(String tenantId, Pageable pageable);
    Page<Receipt> findByTenantIdAndVendorContainingIgnoreCaseOrTenantIdAndFileNameContainingIgnoreCase(String tenantId1, String vendor, String tenantId2, String fileName, Pageable pageable);
    List<Receipt> findByTenantId(String tenantId);

    @Query("SELECT r.ocrModeUsed, COUNT(r) FROM Receipt r WHERE r.tenantId = :tenantId GROUP BY r.ocrModeUsed")
    List<Object[]> getOcrModeStats(@Param("tenantId") String tenantId);

    List<Receipt> findByStatus(String status);
    long countByTenantIdAndStatus(String tenantId, String status);

    @Query("SELECT r FROM Receipt r WHERE r.tenantId = :tenantId " +
           "AND r.amount BETWEEN :minAmount AND :maxAmount " +
           "AND r.reconciliationStatus IN :statuses")
    List<Receipt> findCandidatesByAmountRange(
        @Param("tenantId") String tenantId,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("statuses") List<ReconciliationStatus> statuses
    );
}
