package com.finsight.backend.dto;

import java.util.List;

/**
 * DTO representing the structured JSON array returned by Gemini when parsing a bank statement.
 */
public class GeminiBankStatementResponse {
    private List<ParsedBankTransactionDto> transactions;
    private String error;
    private String trace;
    private List<String> debug;
    private double confidenceScore;

    public List<ParsedBankTransactionDto> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<ParsedBankTransactionDto> transactions) {
        this.transactions = transactions;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    public List<String> getDebug() {
        return debug;
    }

    public void setDebug(List<String> debug) {
        this.debug = debug;
    }
}
