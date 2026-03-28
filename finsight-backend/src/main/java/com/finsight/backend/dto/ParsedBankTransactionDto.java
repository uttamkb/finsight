package com.finsight.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Data Transfer Object representing a single transaction extracted from a Bank Statement PDF.
 * ignoreUnknown = true ensures extra fields from the Python script (e.g. "source") are silently ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParsedBankTransactionDto {
    private String txDate;
    private String description;
    private String vendor;
    private String type; // CREDIT or DEBIT
    private BigDecimal amount;
    private String category; // Suggested category by Gemini
    private Double confidenceScore;
    private String aiReasoning;
    private String originalSnippet;

    // Getters and Setters
    public String getTxDate() { return txDate; }
    public void setTxDate(String txDate) { this.txDate = txDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getAiReasoning() { return aiReasoning; }
    public void setAiReasoning(String aiReasoning) { this.aiReasoning = aiReasoning; }

    public String getOriginalSnippet() { return originalSnippet; }
    public void setOriginalSnippet(String originalSnippet) { this.originalSnippet = originalSnippet; }
}
