package com.finsight.backend.dto;

import lombok.Builder;
import lombok.Data;

public class FileProcessingLog {
    private String fileName;
    private int detectedHeaderRow;
    private String signature;
    private String status; // Pattern Reused / Created / Critical Failure
    private double confidenceScore;
    private int extractedRowCount;

    public FileProcessingLog() {}

    public FileProcessingLog(String fileName, int detectedHeaderRow, String signature, String status, double confidenceScore, int extractedRowCount) {
        this.fileName = fileName;
        this.detectedHeaderRow = detectedHeaderRow;
        this.signature = signature;
        this.status = status;
        this.confidenceScore = confidenceScore;
        this.extractedRowCount = extractedRowCount;
    }

    public static FileProcessingLogBuilder builder() {
        return new FileProcessingLogBuilder();
    }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public int getDetectedHeaderRow() { return detectedHeaderRow; }
    public void setDetectedHeaderRow(int detectedHeaderRow) { this.detectedHeaderRow = detectedHeaderRow; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public int getExtractedRowCount() { return extractedRowCount; }
    public void setExtractedRowCount(int extractedRowCount) { this.extractedRowCount = extractedRowCount; }

    public static class FileProcessingLogBuilder {
        private String fileName;
        private int detectedHeaderRow;
        private String signature;
        private String status;
        private double confidenceScore;
        private int extractedRowCount;

        public FileProcessingLogBuilder fileName(String fileName) { this.fileName = fileName; return this; }
        public FileProcessingLogBuilder detectedHeaderRow(int detectedHeaderRow) { this.detectedHeaderRow = detectedHeaderRow; return this; }
        public FileProcessingLogBuilder signature(String signature) { this.signature = signature; return this; }
        public FileProcessingLogBuilder status(String status) { this.status = status; return this; }
        public FileProcessingLogBuilder confidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; return this; }
        public FileProcessingLogBuilder extractedRowCount(int extractedRowCount) { this.extractedRowCount = extractedRowCount; return this; }
        public FileProcessingLog build() {
            return new FileProcessingLog(fileName, detectedHeaderRow, signature, status, confidenceScore, extractedRowCount);
        }
    }
}
