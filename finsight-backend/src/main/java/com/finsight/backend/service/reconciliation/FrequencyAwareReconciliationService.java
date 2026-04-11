package com.finsight.backend.service.reconciliation;

import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.repository.BankTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FrequencyAwareReconciliationService {

    private final BankTransactionRepository bankTransactionRepository;
    private final CategoryAmountValidator categoryAmountValidator;

    public FrequencyAwareReconciliationService(
            BankTransactionRepository bankTransactionRepository,
            CategoryAmountValidator categoryAmountValidator) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.categoryAmountValidator = categoryAmountValidator;
    }

    /**
     * Checks if a transaction is a likely duplicate.
     * Criteria: Same amount, same category, same month, and category typically only
     * happens once per month (e.g. Electricity, Maintenance, Salary)
     */
    public Optional<String> detectDuplicateMonthlyPayment(BankTransaction currentTxn) {
        if (currentTxn.getCategory() == null || currentTxn.getAmount() == null || currentTxn.getTxDate() == null) {
            return Optional.empty();
        }

        String catName = currentTxn.getCategory().getName();
        if (!isMonthlyRecurringCategory(catName)) {
            return Optional.empty();
        }

        YearMonth currentMonth = YearMonth.from(currentTxn.getTxDate());
        LocalDate startOfMonth = currentMonth.atDay(1);
        LocalDate endOfMonth = currentMonth.atEndOfMonth();

        List<BankTransaction> monthlyTxns = bankTransactionRepository.findByTenantIdAndAccountTypeWithFilters(
                currentTxn.getTenantId(),
                currentTxn.getAccountType(),
                currentTxn.getType(),
                null, // don't care if reconciled or not
                startOfMonth,
                endOfMonth,
                org.springframework.data.domain.Pageable.unpaged()).getContent();

        List<BankTransaction> duplicates = monthlyTxns.stream()
                .filter(tx -> tx.getCategory() != null && tx.getCategory().getName().equals(catName))
                .filter(tx -> tx.getAmount().compareTo(currentTxn.getAmount()) == 0)
                .filter(tx -> !tx.getId().equals(currentTxn.getId())) // don't count itself
                .collect(Collectors.toList());

        if (!duplicates.isEmpty()) {
            return Optional.of(String.format(
                    "Potential duplicate monthly payment for %s: Found %d other transaction(s) with amount %s in %s.",
                    catName, duplicates.size(), currentTxn.getAmount().toString(), currentMonth.toString()));
        }

        return Optional.empty();
    }

    /**
     * Validates if the amount is completely out of typical bounds for the category.
     */
    public Optional<String> detectAnomalousAmount(BankTransaction currentTxn) {
        if (currentTxn.getCategory() == null || currentTxn.getAmount() == null) {
            return Optional.empty();
        }

        String catName = currentTxn.getCategory().getName();
        if (!categoryAmountValidator.isAmountTypicalForCategory(catName, currentTxn.getAmount())) {
            return Optional.of(
                    String.format("Anomalous amount %s for category %s.", currentTxn.getAmount().toString(), catName));
        }
        return Optional.empty();
    }

    private boolean isMonthlyRecurringCategory(String categoryName) {
        return switch (categoryName) {
            case "Electricity", "Water Supply", "Internet", "Lift Maintenance", "Salary", "Security" -> true;
            default -> false;
        };
    }
}
