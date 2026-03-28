package com.finsight.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

public class TrainingSummaryResponse {
    private int totalFilesProcessed;
    private int successfulParses;
    private int failedParses;
    private int patternsLearned;
    private int patternsReused;
    private int patternsFlaggedForReview;
    private List<FileProcessingLog> logs;

    public TrainingSummaryResponse() {}

    public TrainingSummaryResponse(int totalFilesProcessed, int successfulParses, int failedParses, int patternsLearned, int patternsReused, int patternsFlaggedForReview, List<FileProcessingLog> logs) {
        this.totalFilesProcessed = totalFilesProcessed;
        this.successfulParses = successfulParses;
        this.failedParses = failedParses;
        this.patternsLearned = patternsLearned;
        this.patternsReused = patternsReused;
        this.patternsFlaggedForReview = patternsFlaggedForReview;
        this.logs = logs;
    }

    public static TrainingSummaryResponseBuilder builder() {
        return new TrainingSummaryResponseBuilder();
    }

    public int getTotalFilesProcessed() { return totalFilesProcessed; }
    public void setTotalFilesProcessed(int totalFilesProcessed) { this.totalFilesProcessed = totalFilesProcessed; }

    public int getSuccessfulParses() { return successfulParses; }
    public void setSuccessfulParses(int successfulParses) { this.successfulParses = successfulParses; }

    public int getFailedParses() { return failedParses; }
    public void setFailedParses(int failedParses) { this.failedParses = failedParses; }

    public int getPatternsLearned() { return patternsLearned; }
    public void setPatternsLearned(int patternsLearned) { this.patternsLearned = patternsLearned; }

    public int getPatternsReused() { return patternsReused; }
    public void setPatternsReused(int patternsReused) { this.patternsReused = patternsReused; }

    public int getPatternsFlaggedForReview() { return patternsFlaggedForReview; }
    public void setPatternsFlaggedForReview(int patternsFlaggedForReview) { this.patternsFlaggedForReview = patternsFlaggedForReview; }

    public List<FileProcessingLog> getLogs() { return logs; }
    public void setLogs(List<FileProcessingLog> logs) { this.logs = logs; }

    public static class TrainingSummaryResponseBuilder {
        private int totalFilesProcessed;
        private int successfulParses;
        private int failedParses;
        private int patternsLearned;
        private int patternsReused;
        private int patternsFlaggedForReview;
        private List<FileProcessingLog> logs;

        public TrainingSummaryResponseBuilder totalFilesProcessed(int totalFilesProcessed) { this.totalFilesProcessed = totalFilesProcessed; return this; }
        public TrainingSummaryResponseBuilder successfulParses(int successfulParses) { this.successfulParses = successfulParses; return this; }
        public TrainingSummaryResponseBuilder failedParses(int failedParses) { this.failedParses = failedParses; return this; }
        public TrainingSummaryResponseBuilder patternsLearned(int patternsLearned) { this.patternsLearned = patternsLearned; return this; }
        public TrainingSummaryResponseBuilder patternsReused(int patternsReused) { this.patternsReused = patternsReused; return this; }
        public TrainingSummaryResponseBuilder patternsFlaggedForReview(int patternsFlaggedForReview) { this.patternsFlaggedForReview = patternsFlaggedForReview; return this; }
        public TrainingSummaryResponseBuilder logs(List<FileProcessingLog> logs) { this.logs = logs; return this; }
        public TrainingSummaryResponse build() {
            return new TrainingSummaryResponse(totalFilesProcessed, successfulParses, failedParses, patternsLearned, patternsReused, patternsFlaggedForReview, logs);
        }
    }
}

