package com.finsight.backend.dto;

public class ReconciliationStatsDto {
    private long unresolvedCount;
    private long totalMatched;

    public ReconciliationStatsDto(long unresolvedCount, long totalMatched) {
        this.unresolvedCount = unresolvedCount;
        this.totalMatched = totalMatched;
    }

    // Getters and Setters
    public long getUnresolvedCount() { return unresolvedCount; }
    public void setUnresolvedCount(long unresolvedCount) { this.unresolvedCount = unresolvedCount; }
    public long getTotalMatched() { return totalMatched; }
    public void setTotalMatched(long totalMatched) { this.totalMatched = totalMatched; }
}
