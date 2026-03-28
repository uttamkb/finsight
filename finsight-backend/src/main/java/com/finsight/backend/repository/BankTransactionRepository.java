package com.finsight.backend.repository;

import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.ReconciliationStatus;
import com.finsight.backend.dto.VendorInsightDto;
import com.finsight.backend.dto.CategoryInsightDto;
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
    
    Page<BankTransaction> findByTenantId(String tenantId, Pageable pageable);

    @Query(value = "SELECT b FROM BankTransaction b LEFT JOIN FETCH b.category LEFT JOIN FETCH b.receipt WHERE b.tenantId = :tenantId",
           countQuery = "SELECT COUNT(b) FROM BankTransaction b WHERE b.tenantId = :tenantId")
    Page<BankTransaction> findByTenantIdWithCategory(@Param("tenantId") String tenantId, Pageable pageable);

    @Query(value = "SELECT b FROM BankTransaction b LEFT JOIN FETCH b.category LEFT JOIN FETCH b.receipt " +
                   "WHERE b.tenantId = :tenantId AND " +
                   "(:reconciled = true AND b.reconciliationStatus = 'MATCHED' OR :reconciled = false AND b.reconciliationStatus <> 'MATCHED')",
           countQuery = "SELECT COUNT(b) FROM BankTransaction b WHERE b.tenantId = :tenantId AND " +
                        "(:reconciled = true AND b.reconciliationStatus = 'MATCHED' OR :reconciled = false AND b.reconciliationStatus <> 'MATCHED')")
    Page<BankTransaction> findByTenantIdWithCategoryAndReconciled(
        @Param("tenantId") String tenantId,
        @Param("reconciled") boolean reconciled,
        Pageable pageable);
    
    boolean existsByReferenceNumberAndTenantId(String referenceNumber, String tenantId);
    
    Page<BankTransaction> findByTenantIdOrderByTxDateDesc(String tenantId, Pageable pageable);

    @Query("SELECT b FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.accountType = :accountType ORDER BY b.txDate DESC")
    Page<BankTransaction> findByTenantIdAndAccountTypeOrderByTxDateDesc(
        @Param("tenantId") String tenantId, 
        @Param("accountType") BankTransaction.AccountType accountType, 
        Pageable pageable
    );

    @Query("SELECT b FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.reconciliationStatus <> 'MATCHED' AND b.type = :type")
    List<BankTransaction> findByTenantIdAndReconciledFalseAndType(@Param("tenantId") String tenantId, @Param("type") BankTransaction.TransactionType type);

    @Query("SELECT b.receipt.id FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.receipt IS NOT NULL")
    List<Long> findLinkedReceiptIds(@Param("tenantId") String tenantId);

    @Query("SELECT b FROM BankTransaction b WHERE b.tenantId = :tenantId " +
           "AND b.txDate BETWEEN :startDate AND :endDate " +
           "AND b.amount = :amount AND b.reconciliationStatus <> 'MATCHED'")
    List<BankTransaction> findUnreconciledByDateRangeAndAmount(
        @Param("tenantId") String tenantId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("amount") BigDecimal amount
    );

    @Query("SELECT new com.finsight.backend.dto.VendorInsightDto(b.description, SUM(b.amount), COUNT(b)) " +
           "FROM BankTransaction b " +
           "WHERE b.tenantId = :tenantId AND b.type = 'DEBIT' AND b.accountType = :accountType " +
           "GROUP BY b.description " +
           "ORDER BY SUM(b.amount) DESC")
    List<VendorInsightDto> getTopSpendingByVendor(
        @Param("tenantId") String tenantId, 
        @Param("accountType") BankTransaction.AccountType accountType,
        Pageable pageable
    );

    @Query("SELECT new com.finsight.backend.dto.CategoryInsightDto(c.name, SUM(b.amount)) " +
           "FROM BankTransaction b JOIN b.category c " +
           "WHERE b.tenantId = :tenantId AND b.type = 'DEBIT' AND b.accountType = :accountType " +
           "GROUP BY c.name " +
           "ORDER BY SUM(b.amount) DESC")
    List<CategoryInsightDto> getTopSpendingByCategory(
        @Param("tenantId") String tenantId,
        @Param("accountType") BankTransaction.AccountType accountType
    );

    @Query("SELECT COUNT(b) FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.reconciliationStatus <> 'MATCHED' AND b.accountType = :accountType")
    long countUnreconciledByTenantIdAndAccountType(
        @Param("tenantId") String tenantId,
        @Param("accountType") BankTransaction.AccountType accountType
    );

    @Query("SELECT SUM(b.amount) FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.type = :type AND b.txDate BETWEEN :start AND :end AND b.accountType = :accountType")
    BigDecimal sumAmountByTenantIdAndTypeAndDateRangeAndAccountType(
        @Param("tenantId") String tenantId,
        @Param("type") BankTransaction.TransactionType type,
        @Param("start") LocalDate start,
        @Param("end") LocalDate end,
        @Param("accountType") BankTransaction.AccountType accountType
    );

    @Query("SELECT b FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.accountType = :accountType ORDER BY b.txDate DESC")
    List<BankTransaction> findAllByTenantIdAndAccountTypeOrderByTxDateDesc(
        @Param("tenantId") String tenantId, 
        @Param("accountType") BankTransaction.AccountType accountType,
        Pageable pageable
    );

    @Query("SELECT b FROM BankTransaction b WHERE b.tenantId = :tenantId AND " +
           "(:reconciled = true AND b.reconciliationStatus = 'MATCHED' OR :reconciled = false AND b.reconciliationStatus <> 'MATCHED') " +
           "AND b.accountType = :accountType ORDER BY b.txDate DESC")
    Page<BankTransaction> findByTenantIdAndAccountTypeWithCategoryAndReconciled(
        @Param("tenantId") String tenantId,
        @Param("reconciled") boolean reconciled,
        @Param("accountType") BankTransaction.AccountType accountType,
        Pageable pageable
    );

    @Query("SELECT b FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.accountType = :accountType " +
           "AND (:type IS NULL OR b.type = :type) " +
           "AND (:reconciled IS NULL OR " +
           "    (:reconciled = true AND b.reconciliationStatus = 'MATCHED') OR " +
           "    (:reconciled = false AND b.reconciliationStatus <> 'MATCHED')) " +
           "AND (:startDate IS NULL OR b.txDate >= :startDate) " +
           "AND (:endDate IS NULL OR b.txDate <= :endDate) " +
           "ORDER BY b.txDate DESC")
    Page<BankTransaction> findByTenantIdAndAccountTypeWithFilters(
        @Param("tenantId") String tenantId,
        @Param("accountType") BankTransaction.AccountType accountType,
        @Param("type") BankTransaction.TransactionType type,
        @Param("reconciled") Boolean reconciled,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        Pageable pageable
    );

    @Query("SELECT b FROM BankTransaction b WHERE b.tenantId = :tenantId AND b.accountType = :accountType ORDER BY b.txDate DESC")
    Page<BankTransaction> findByTenantIdAndAccountTypeWithCategory(
        @Param("tenantId") String tenantId,
        @Param("accountType") BankTransaction.AccountType accountType,
        Pageable pageable
    );

    @Query("SELECT b FROM BankTransaction b " +
           "WHERE b.tenantId = :tenantId AND b.accountType = :accountType " +
           "AND b.reconciliationStatus IN :statuses")
    List<BankTransaction> findByTenantIdAndAccountTypeAndReconciliationStatusIn(
        @Param("tenantId") String tenantId,
        @Param("accountType") BankTransaction.AccountType accountType,
        @Param("statuses") List<ReconciliationStatus> statuses
    );

    long countByTenantIdAndAccountTypeAndReconciliationStatus(
        String tenantId, 
        BankTransaction.AccountType accountType, 
        ReconciliationStatus status
    );
    @Query("SELECT new com.finsight.backend.dto.VendorInsightDto(b.description, SUM(b.amount), COUNT(b)) " +
           "FROM BankTransaction b JOIN b.category c " +
           "WHERE b.tenantId = :tenantId AND c.name = :categoryName AND b.type = 'DEBIT' AND b.accountType = :accountType " +
           "GROUP BY b.description " +
           "ORDER BY SUM(b.amount) DESC")
    List<VendorInsightDto> getTopSpendingByCategoryAndVendor(
        @Param("tenantId") String tenantId,
        @Param("categoryName") String categoryName,
        @Param("accountType") BankTransaction.AccountType accountType,
        Pageable pageable
    );

    @Query("SELECT b FROM BankTransaction b JOIN b.category c " +
           "WHERE b.tenantId = :tenantId AND c.name = :categoryName AND b.accountType = :accountType " +
           "ORDER BY b.txDate DESC")
    List<BankTransaction> findRecentByCategory(
        @Param("tenantId") String tenantId,
        @Param("categoryName") String categoryName,
        @Param("accountType") BankTransaction.AccountType accountType,
        Pageable pageable
    );
}
