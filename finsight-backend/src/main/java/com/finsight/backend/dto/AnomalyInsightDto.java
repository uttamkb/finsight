package com.finsight.backend.dto;

import java.math.BigDecimal;

public class AnomalyInsightDto {
    private String description;
    private String reason;
    private BigDecimal amount;
    private String date;

    // Getters and Setters
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
}
