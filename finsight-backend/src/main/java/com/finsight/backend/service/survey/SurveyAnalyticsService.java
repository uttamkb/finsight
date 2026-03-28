package com.finsight.backend.service.survey;

import com.finsight.backend.entity.survey.SurveyResponse;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SurveyAnalyticsService {

    private final SurveyResponseRepository responseRepository;

    public SurveyAnalyticsService(SurveyResponseRepository responseRepository) {
        this.responseRepository = responseRepository;
    }

    /**
     * Returns aggregated results suitable for the API dashboard.
     */
    public Map<String, Object> getAggregatedResults(Long surveyId) {
        List<SurveyResponse> responses = responseRepository.findBySurveyId(surveyId).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .collect(Collectors.toList());

        // Numeric questions (1–5 scale)
        Map<String, List<SurveyResponse>> groupedNumeric = responses.stream()
                .filter(r -> isNumeric(r.getAnswer()))
                .collect(Collectors.groupingBy(SurveyResponse::getQuestion));

        Map<String, Double> averageRatings = new LinkedHashMap<>();
        Map<String, Map<String, Long>> ratingDistributions = new LinkedHashMap<>();

        groupedNumeric.forEach((question, respList) -> {
            double avg = respList.stream()
                    .mapToDouble(r -> parseLenient(r.getAnswer()))
                    .average()
                    .orElse(0.0);
            averageRatings.put(question, Math.round(avg * 100.0) / 100.0);

            // Distribution e.g. {"1": 2, "2": 0, "3": 5, "4": 10, "5": 8}
            Map<String, Long> dist = respList.stream()
                    .collect(Collectors.groupingBy(r -> String.valueOf((int) parseLenient(r.getAnswer())), TreeMap::new, Collectors.counting()));
            ratingDistributions.put(question, dist);
        });

        // Text questions
        Map<String, List<String>> textResponses = responses.stream()
                .filter(r -> !isNumeric(r.getAnswer()) && r.getAnswer() != null && !r.getAnswer().isBlank())
                .collect(Collectors.groupingBy(SurveyResponse::getQuestion,
                        Collectors.mapping(SurveyResponse::getAnswer, Collectors.toList())));

        Map<String, Object> results = new LinkedHashMap<>();
        results.put("surveyId", surveyId);
        results.put("totalResponses", responses.stream().map(SurveyResponse::getTimestamp).distinct().count());
        results.put("averageRatings", averageRatings);
        results.put("ratingDistributions", ratingDistributions);
        results.put("textFeedback", textResponses);

        return results;
    }

    /**
     * Builds a compact, token-efficient context string for Gemini.
     * Pre-aggregates ratings and groups text feedback to avoid sending raw redundant rows.
     */
    public String buildGeminiContext(Long surveyId) {
        List<SurveyResponse> responses = responseRepository.findBySurveyId(surveyId).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .collect(Collectors.toList());
        if (responses.isEmpty()) return "";

        long totalRespondents = responses.stream().map(SurveyResponse::getTimestamp).distinct().count();
        StringBuilder ctx = new StringBuilder();
        ctx.append(String.format("Total Respondents: %d\n\n", totalRespondents));

        // --- Numeric (Rating) Summary ---
        Map<String, List<Double>> numericByQuestion = responses.stream()
                .filter(r -> isNumeric(r.getAnswer()))
                .collect(Collectors.groupingBy(SurveyResponse::getQuestion,
                        Collectors.mapping(r -> parseLenient(r.getAnswer()), Collectors.toList())));

        ctx.append("=== Facility Ratings (Scale 1–5) ===\n");
        numericByQuestion.forEach((question, scores) -> {
            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            long count = scores.size();
            Map<Integer, Long> dist = scores.stream()
                    .collect(Collectors.groupingBy(Double::intValue, TreeMap::new, Collectors.counting()));
            ctx.append(String.format("• %s | Avg: %.2f/5 | n=%d | Distribution: %s\n",
                    question, avg, count, dist));
        });

        // --- Text Feedback (Deduplicated & Consolidated) ---
        ctx.append("\n=== Open-Ended Feedback ===\n");
        Map<String, List<String>> textByQuestion = responses.stream()
                .filter(r -> !isNumeric(r.getAnswer()) && r.getAnswer() != null && !r.getAnswer().isBlank())
                .collect(Collectors.groupingBy(SurveyResponse::getQuestion,
                        Collectors.mapping(SurveyResponse::getAnswer, Collectors.toList())));

        textByQuestion.forEach((question, answers) -> {
            // Deduplicate and cap at 15 unique responses to save tokens
            List<String> uniqueAnswers = answers.stream().distinct().limit(15).collect(Collectors.toList());
            ctx.append(String.format("• %s (%d responses):\n", question, answers.size()));
            uniqueAnswers.forEach(ans -> ctx.append(String.format("  - \"%s\"\n", ans)));
        });

        return ctx.toString();
    }

    private boolean isNumeric(String str) {
        if (str == null) return false;
        String clean = str.trim();
        if (clean.isEmpty()) return false;
        
        try {
            Double.parseDouble(clean);
            return true;
        } catch (NumberFormatException e) {
            // Extraction check: starts with a digit
            if (Character.isDigit(clean.charAt(0))) {
                try {
                    String firstPart = clean.split("[^0-9.]")[0];
                    Double.parseDouble(firstPart);
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
            return false;
        }
    }

    private double parseLenient(String str) {
        String clean = str.trim();
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException e) {
            String firstPart = clean.split("[^0-9.]")[0];
            return Double.parseDouble(firstPart);
        }
    }
}
