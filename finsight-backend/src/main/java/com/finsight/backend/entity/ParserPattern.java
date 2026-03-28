package com.finsight.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "parser_patterns", 
       indexes = {@Index(name = "idx_pattern_tenant", columnList = "tenantId"),
                  @Index(name = "idx_pattern_sig", columnList = "signature")})
public class ParserPattern {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String signature; // Normalized header join or structural hash

    private String patternGroupId; // ID representing a cluster of similar formats

    @Column(columnDefinition = "TEXT", nullable = false)
    private String columnMapping; // JSON mapping: { "date": 0, "description": 1, ... }

    private double confidenceScore; // 0.0 to 1.0

    private int usageCount;

    private LocalDateTime lastUsedAt;
    
    private LocalDateTime createdAt = LocalDateTime.now();

    private String sourceFormat; // EXCEL, CSV, etc.
    
    private Integer headerRowIndex; // Row index where headers were detected

    public ParserPattern() {}

    public ParserPattern(Long id, String tenantId, String signature, String patternGroupId, String columnMapping, double confidenceScore, int usageCount, LocalDateTime lastUsedAt, LocalDateTime createdAt, String sourceFormat, Integer headerRowIndex) {
        this.id = id;
        this.tenantId = tenantId;
        this.signature = signature;
        this.patternGroupId = patternGroupId;
        this.columnMapping = columnMapping;
        this.confidenceScore = confidenceScore;
        this.usageCount = usageCount;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.sourceFormat = sourceFormat;
        this.headerRowIndex = headerRowIndex;
    }

    public static ParserPatternBuilder builder() {
        return new ParserPatternBuilder();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getPatternGroupId() { return patternGroupId; }
    public void setPatternGroupId(String patternGroupId) { this.patternGroupId = patternGroupId; }

    public String getColumnMapping() { return columnMapping; }
    public void setColumnMapping(String columnMapping) { this.columnMapping = columnMapping; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }

    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getSourceFormat() { return sourceFormat; }
    public void setSourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; }

    public Integer getHeaderRowIndex() { return headerRowIndex; }
    public void setHeaderRowIndex(Integer headerRowIndex) { this.headerRowIndex = headerRowIndex; }

    public static class ParserPatternBuilder {
        private Long id;
        private String tenantId;
        private String signature;
        private String patternGroupId;
        private String columnMapping;
        private double confidenceScore;
        private int usageCount;
        private LocalDateTime lastUsedAt;
        private LocalDateTime createdAt;
        private String sourceFormat;
        private Integer headerRowIndex;

        public ParserPatternBuilder id(Long id) { this.id = id; return this; }
        public ParserPatternBuilder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public ParserPatternBuilder signature(String signature) { this.signature = signature; return this; }
        public ParserPatternBuilder patternGroupId(String patternGroupId) { this.patternGroupId = patternGroupId; return this; }
        public ParserPatternBuilder columnMapping(String columnMapping) { this.columnMapping = columnMapping; return this; }
        public ParserPatternBuilder confidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; return this; }
        public ParserPatternBuilder usageCount(int usageCount) { this.usageCount = usageCount; return this; }
        public ParserPatternBuilder lastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; return this; }
        public ParserPatternBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public ParserPatternBuilder sourceFormat(String sourceFormat) { this.sourceFormat = sourceFormat; return this; }
        public ParserPatternBuilder headerRowIndex(Integer headerRowIndex) { this.headerRowIndex = headerRowIndex; return this; }
        public ParserPattern build() {
            return new ParserPattern(id, tenantId, signature, patternGroupId, columnMapping, confidenceScore, usageCount, lastUsedAt, createdAt, sourceFormat, headerRowIndex);
        }
    }
}
