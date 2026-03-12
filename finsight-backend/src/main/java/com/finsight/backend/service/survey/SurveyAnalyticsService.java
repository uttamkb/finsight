package com.finsight.backend.service.survey;

import com.finsight.backend.entity.survey.SurveyResponse;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SurveyAnalyticsService {

    private final SurveyResponseRepository responseRepository;

    public SurveyAnalyticsService(SurveyResponseRepository responseRepository) {
        this.responseRepository = responseRepository;
    }

    public Map<String, Object> getAggregatedResults(Long surveyId) {
        List<SurveyResponse> responses = responseRepository.findBySurveyId(surveyId);
        
        // Filter for rating questions (1-5)
        Map<String, List<SurveyResponse>> groupedByQuestion = responses.stream()
                .filter(r -> isNumeric(r.getAnswer()))
                .collect(Collectors.groupingBy(SurveyResponse::getQuestion));

        Map<String, Double> averageRatings = new HashMap<>();
        groupedByQuestion.forEach((question, respList) -> {
            double avg = respList.stream()
                    .mapToDouble(r -> Double.parseDouble(r.getAnswer()))
                    .average()
                    .orElse(0.0);
            averageRatings.put(question, avg);
        });

        Map<String, Object> results = new HashMap<>();
        results.put("surveyId", surveyId);
        results.put("totalResponses", responses.stream().map(SurveyResponse::getTimestamp).distinct().count());
        results.put("averageRatings", averageRatings);
        
        return results;
    }

    private boolean isNumeric(String str) {
        if (str == null) return false;
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
