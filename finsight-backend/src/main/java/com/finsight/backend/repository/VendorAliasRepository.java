package com.finsight.backend.repository;

import com.finsight.backend.entity.VendorAlias;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorAliasRepository extends JpaRepository<VendorAlias, Long> {
    Optional<VendorAlias> findByTenantIdAndAliasName(String tenantId, String aliasName);

    List<VendorAlias> findByTenantIdOrderByApprovalCountDesc(String tenantId);
}
