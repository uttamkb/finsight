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

    public static AuditTrailDto from(AuditTrail audit) {
        if (audit == null) return null;
        AuditTrailDto dto = new AuditTrailDto();
        dto.id = audit.getId();
        dto.transaction = BankTransactionDto.from(audit.getTransaction());
        dto.issueType = audit.getIssueType() != null ? audit.getIssueType().name() : null;
        dto.description = audit.getIssueDescription(); // Map entity's issueDescription to UI's description
        dto.resolved = audit.getResolved();
        dto.createdAt = audit.getCreatedAt();
        dto.similarityScore = audit.getSimilarityScore();
        dto.matchType = audit.getMatchType();
        return dto;
    }

    // Getters
    public Long getId()                      { return id; }
    public BankTransactionDto getTransaction() { return transaction; }
    public String getIssueType()             { return issueType; }
    public String getDescription()           { return description; }
    public Boolean getResolved()             { return resolved; }
    public LocalDateTime getCreatedAt()      { return createdAt; }
    public Double getSimilarityScore()       { return similarityScore; }
    public String getMatchType()             { return matchType; }
}
