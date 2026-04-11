package com.finsight.backend.repository;

import com.finsight.backend.entity.CategoryKeywordMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryKeywordMappingRepository extends JpaRepository<CategoryKeywordMapping, Long> {
    List<CategoryKeywordMapping> findByTenantIdOrderByPriorityDesc(String tenantId);
}
