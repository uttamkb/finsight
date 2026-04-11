package com.finsight.backend.dto;

import com.finsight.backend.entity.AuditTrail;

import java.time.LocalDateTime;

/**
 * DTO representing an AuditTrail entry for API responses.
 * Maps 'issueDescription' to 'description' for UI compatibility.
 */
public class AuditTrailDto {
    private Long id;
    private BankTransactionDto transaction;
    private String issueType;
    private String description;
    private Boolean resolved;
    private LocalDateTime createdAt;
    private Double similarityScore;
    private String matchType;

    // New detailed score breakdown
    private Double amountScore;
    private Double dateScore;
    private Double vendorScore;
    private String amountReasoning;
    private String dateReasoning;
    private String vendorReasoning;

    public static AuditTrailDto from(AuditTrail audit) {
        if (audit == null)
            return null;
        AuditTrailDto dto = new AuditTrailDto();
        dto.id = audit.getId();
        dto.transaction = BankTransactionDto.from(audit.getTransaction());
        dto.issueType = audit.getIssueType() != null ? audit.getIssueType().name() : null;
        dto.description = audit.getIssueDescription(); // Map entity's issueDescription to UI's description
        dto.resolved = audit.getResolved();
        dto.createdAt = audit.getCreatedAt();
        dto.similarityScore = audit.getSimilarityScore();
        dto.matchType = audit.getMatchType();

        dto.amountScore = audit.getAmountScore();
        dto.dateScore = audit.getDateScore();
        dto.vendorScore = audit.getVendorScore();
        dto.amountReasoning = audit.getAmountReasoning();
        dto.dateReasoning = audit.getDateReasoning();
        dto.vendorReasoning = audit.getVendorReasoning();

        return dto;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public BankTransactionDto getTransaction() {
        return transaction;
    }

    public String getIssueType() {
        return issueType;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getResolved() {
        return resolved;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Double getSimilarityScore() {
        return similarityScore;
    }

    public String getMatchType() {
        return matchType;
    }

    public Double getAmountScore() {
        return amountScore;
    }

    public Double getDateScore() {
        return dateScore;
    }

    public Double getVendorScore() {
        return vendorScore;
    }

    public String getAmountReasoning() {
        return amountReasoning;
    }

    public String getDateReasoning() {
        return dateReasoning;
    }

    public String getVendorReasoning() {
        return vendorReasoning;
    }
}
