package com.finsight.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data Transfer Object representing a single transaction extracted from a Bank Statement PDF by Gemini.
 */
public class ParsedBankTransactionDto {
    private LocalDate txDate;
    private String description;
    private String type; // CREDIT or DEBIT
    private BigDecimal amount;
    private String category; // Suggested category by Gemini

    // Getters and Setters
    public LocalDate getTxDate() { return txDate; }
    public void setTxDate(LocalDate txDate) { this.txDate = txDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
