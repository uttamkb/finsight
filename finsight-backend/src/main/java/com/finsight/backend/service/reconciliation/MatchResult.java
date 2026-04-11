package com.finsight.backend.service.reconciliation;

public class MatchResult {
    private final double score;
    private final String reasoning;

    public MatchResult(double score, String reasoning) {
        this.score = score;
        this.reasoning = reasoning;
    }

    public double getScore() {
        return score;
    }

    public String getReasoning() {
        return reasoning;
    }
}
