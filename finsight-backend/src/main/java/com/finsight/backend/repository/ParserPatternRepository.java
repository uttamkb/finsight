package com.finsight.backend.repository;

import com.finsight.backend.entity.ParserPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParserPatternRepository extends JpaRepository<ParserPattern, Long> {
    
    Optional<ParserPattern> findBySignatureAndTenantId(String signature, String tenantId);
    
    List<ParserPattern> findByTenantId(String tenantId);

    List<ParserPattern> findByPatternGroupId(String patternGroupId);
}
