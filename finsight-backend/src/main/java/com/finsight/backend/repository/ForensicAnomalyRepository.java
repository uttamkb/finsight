package com.finsight.backend.repository;

import com.finsight.backend.entity.ForensicAnomaly;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ForensicAnomalyRepository extends JpaRepository<ForensicAnomaly, Long> {
    List<ForensicAnomaly> findByTenantIdOrderByTxDateDesc(String tenantId);
}
