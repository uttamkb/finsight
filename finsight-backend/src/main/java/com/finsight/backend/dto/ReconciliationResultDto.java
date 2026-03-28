package com.finsight.backend.dto;

import java.util.List;

public class ReconciliationResultDto {
    private int matchedCount;
    private int reconciledCount;
    private int manualReviewCount;
    private List<String> logs;

    public ReconciliationResultDto() {}

    public ReconciliationResultDto(int matchedCount, int manualReviewCount, List<String> logs) {
        this.matchedCount = matchedCount;
        this.reconciledCount = matchedCount; // Mapping for compatibility
        this.manualReviewCount = manualReviewCount;
        this.logs = logs;
    }

    public int getMatchedCount() { return matchedCount; }
    public void setMatchedCount(int matchedCount) { 
        this.matchedCount = matchedCount;
        this.reconciledCount = matchedCount;
    }

    public int getReconciledCount() { return reconciledCount; }
    public void setReconciledCount(int reconciledCount) { this.reconciledCount = reconciledCount; }

    public int getManualReviewCount() { return manualReviewCount; }
    public void setManualReviewCount(int manualReviewCount) { this.manualReviewCount = manualReviewCount; }

    public List<String> getLogs() { return logs; }
    public void setLogs(List<String> logs) { this.logs = logs; }
}
