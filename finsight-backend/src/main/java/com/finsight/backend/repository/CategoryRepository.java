package com.finsight.backend.repository;

import com.finsight.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByTenantIdOrderByNameAsc(String tenantId);
    List<Category> findByTenantIdAndType(String tenantId, Category.CategoryType type);
    java.util.Optional<Category> findByNameAndTenantId(String name, String tenantId);
}
