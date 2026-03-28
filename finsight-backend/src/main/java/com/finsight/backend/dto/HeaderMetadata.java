package com.finsight.backend.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

public class HeaderMetadata {
    private int headerRowIndex;
    private List<String> headers;
    private Map<String, Integer> columnMapping;
    private double confidenceScore;
    private String delimiter;

    public HeaderMetadata() {}

    public HeaderMetadata(int headerRowIndex, List<String> headers, Map<String, Integer> columnMapping, double confidenceScore, String delimiter) {
        this.headerRowIndex = headerRowIndex;
        this.headers = headers;
        this.columnMapping = columnMapping;
        this.confidenceScore = confidenceScore;
        this.delimiter = delimiter;
    }

    public static HeaderMetadataBuilder builder() {
        return new HeaderMetadataBuilder();
    }

    public int getHeaderRowIndex() { return headerRowIndex; }
    public void setHeaderRowIndex(int headerRowIndex) { this.headerRowIndex = headerRowIndex; }

    public List<String> getHeaders() { return headers; }
    public void setHeaders(List<String> headers) { this.headers = headers; }

    public Map<String, Integer> getColumnMapping() { return columnMapping; }
    public void setColumnMapping(Map<String, Integer> columnMapping) { this.columnMapping = columnMapping; }

    public double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; }

    public String getDelimiter() { return delimiter; }
    public void setDelimiter(String delimiter) { this.delimiter = delimiter; }

    public static class HeaderMetadataBuilder {
        private int headerRowIndex;
        private List<String> headers;
        private Map<String, Integer> columnMapping;
        private double confidenceScore;
        private String delimiter;

        public HeaderMetadataBuilder headerRowIndex(int headerRowIndex) { this.headerRowIndex = headerRowIndex; return this; }
        public HeaderMetadataBuilder headers(List<String> headers) { this.headers = headers; return this; }
        public HeaderMetadataBuilder columnMapping(Map<String, Integer> columnMapping) { this.columnMapping = columnMapping; return this; }
        public HeaderMetadataBuilder confidenceScore(double confidenceScore) { this.confidenceScore = confidenceScore; return this; }
        public HeaderMetadataBuilder delimiter(String delimiter) { this.delimiter = delimiter; return this; }
        public HeaderMetadata build() {
            return new HeaderMetadata(headerRowIndex, headers, columnMapping, confidenceScore, delimiter);
        }
    }
}
