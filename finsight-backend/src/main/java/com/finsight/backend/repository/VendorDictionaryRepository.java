package com.finsight.backend.repository;

import com.finsight.backend.entity.VendorDictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorDictionaryRepository extends JpaRepository<VendorDictionary, Long> {
    List<VendorDictionary> findAllByTenantId(String tenantId);
    Optional<VendorDictionary> findByTenantIdAndVendorName(String tenantId, String vendorName);
    boolean existsByTenantIdAndVendorName(String tenantId, String vendorName);
}
