package com.finsight.backend.dto;

import java.util.List;

public class GeminiAnomalyResponse {
    private List<AnomalyInsightDto> anomalies;

    public List<AnomalyInsightDto> getAnomalies() {
        return anomalies;
    }

    public void setAnomalies(List<AnomalyInsightDto> anomalies) {
        this.anomalies = anomalies;
    }
}
