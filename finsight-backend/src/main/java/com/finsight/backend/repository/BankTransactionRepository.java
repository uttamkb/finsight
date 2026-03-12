package com.finsight.backend.repository;

import com.finsight.backend.entity.BankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    
    // For pagination and filtering
    Page<BankTransaction> findByTenantId(String tenantId, Pageable pageable);
    
    // For deduplication
    boolean existsByReferenceNumberAndTenantId(String referenceNumber, String tenantId);
    Page<BankTransaction> findByTenantIdOrderByTxDateDesc(String tenantId, Pageable pageable);
    List<BankTransaction> findByTenantIdAndReconciledFalseAndType(String tenantId, BankTransaction.TransactionType type);

    @Query("SELECT b.receipt.id FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.receipt IS NOT NULL")
    List<Long> findLinkedReceiptIds(@Param("tenantId") String tenantId);

    @Query("SELECT b FROM BankTransaction b WHERE b.tenantId = :tenantId " +
           "AND b.txDate BETWEEN :startDate AND :endDate " +
           "AND b.amount = :amount AND b.reconciled = false")
    List<BankTransaction> findUnreconciledByDateRangeAndAmount(
        @Param("tenantId") String tenantId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("amount") BigDecimal amount
    );

    @Query("SELECT new com.finsight.backend.dto.VendorInsightDto(b.description, SUM(b.amount), COUNT(b)) " +
           "FROM BankTransaction b " +
           "WHERE b.tenantId = :tenantId AND b.type = 'DEBIT' " +
           "GROUP BY b.description " +
           "ORDER BY SUM(b.amount) DESC")
    List<com.finsight.backend.dto.VendorInsightDto> getTopSpendingByVendor(
        @Param("tenantId") String tenantId, 
        Pageable pageable
    );

    @Query("SELECT new com.finsight.backend.dto.CategoryInsightDto(c.name, SUM(b.amount)) " +
           "FROM BankTransaction b JOIN b.category c " +
           "WHERE b.tenantId = :tenantId AND b.type = 'DEBIT' " +
           "GROUP BY c.name " +
           "ORDER BY SUM(b.amount) DESC")
    List<com.finsight.backend.dto.CategoryInsightDto> getTopSpendingByCategory(
        @Param("tenantId") String tenantId
    );

    @Query("SELECT COUNT(b) FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.reconciled = false")
    long countUnreconciledByTenantId(@Param("tenantId") String tenantId);

    @Query("SELECT SUM(b.amount) FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.type = :type AND b.txDate BETWEEN :start AND :end")
    BigDecimal sumAmountByTenantIdAndTypeAndDateRange(
        @Param("tenantId") String tenantId,
        @Param("type") BankTransaction.TransactionType type,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end
    );

    @Query("SELECT b FROM BankTransaction b WHERE b.tenantId = :tenantId ORDER BY b.txDate DESC")
    List<BankTransaction> findAllByTenantIdOrderByTxDateDesc(@Param("tenantId") String tenantId, Pageable pageable);
}
