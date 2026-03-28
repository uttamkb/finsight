package com.finsight.backend.repository;

import com.finsight.backend.entity.StatementUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface StatementUploadRepository extends JpaRepository<StatementUpload, Long> {
    Optional<StatementUpload> findByFileId(String fileId);
    Optional<StatementUpload> findByFileHashAndTenantIdAndStatus(String fileHash, String tenantId, String status);

    @Transactional
    @Modifying
    @Query("DELETE FROM StatementUpload s WHERE s.createdAt < :date")
    void deleteOlderThan(@Param("date") LocalDateTime date);

    List<StatementUpload> findByCreatedAtBefore(LocalDateTime date);
    List<StatementUpload> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
