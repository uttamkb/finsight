package com.finsight.backend.service.survey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.repository.survey.SurveyActionItemRepository;
import com.finsight.backend.repository.survey.SurveyInsightRepository;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.entity.survey.SurveyActionItem;
import com.finsight.backend.service.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
public class SurveyAiService {

    private static final Logger log = LoggerFactory.getLogger(SurveyAiService.class);

    @org.springframework.beans.factory.annotation.Value("${ai.gemini.model:models/gemini-2.5-flash}")
    private String geminiModel;

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/";

    private final AppConfigService appConfigService;
    private final SurveyInsightRepository insightRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyActionItemRepository actionItemRepository;
    private final SurveyAnalyticsService analyticsService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SurveyAiService(AppConfigService appConfigService,
                           SurveyInsightRepository insightRepository,
                           SurveyRepository surveyRepository,
                           SurveyActionItemRepository actionItemRepository,
                           SurveyAnalyticsService analyticsService,
                           HttpClient httpClient,
                           ObjectMapper objectMapper) {
        this.appConfigService = appConfigService;
        this.insightRepository = insightRepository;
        this.surveyRepository = surveyRepository;
        this.actionItemRepository = actionItemRepository;
        this.analyticsService = analyticsService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a structured MC Action Plan from aggregated survey responses.
     * Stores the result as JSON in Survey.actionPlan.
     */
    public void generateInsights(Long surveyId) throws Exception {
        String aggregatedContext = analyticsService.buildGeminiContext(surveyId);
        if (aggregatedContext.isBlank()) {
            log.warn("No responses for surveyId: {}. Skipping AI analysis.", surveyId);
            return;
        }

        AppConfig config = appConfigService.getConfig();
        String apiKey = config.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Gemini API Key is not configured.");
        }

        log.info("Sending {} chars of aggregated context to Gemini for surveyId: {}",
                aggregatedContext.length(), surveyId);

        String prompt = """
                You are the analytics engine for a residential apartment complex management committee.
                Analyze the following aggregated resident survey data.

                Return a JSON object with EXACTLY ONE field:

                "mcActionPlan": an array of action items for the Management Committee, each with:
                  - "priority": "HIGH" | "MEDIUM" | "LOW"
                  - "facility": string (area/service being addressed)
                  - "action": string (specific, concrete action to take)
                  - "timeline": string (e.g., "Within 1 week", "Within 1 month", "Next quarter")
                  - "expectedOutcome": string (measurable outcome expected)

                Rules:
                - Minimum 5 action items, maximum 15
                - Prioritize HIGH for facilities rated < 3.0 avg
                - Be specific and actionable, not generic
                - Focus on what the MC can actually do

                Survey Data (Pre-Aggregated):
                """ + aggregatedContext;

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_BASE_URL + geminiModel + ":generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Gemini response status: {} for surveyId: {}", response.statusCode(), surveyId);

        if (response.statusCode() == 200) {
            var rootNode = objectMapper.readTree(response.body());
            String jsonText = rootNode.path("candidates").get(0)
                    .path("content").path("parts").get(0).path("text").asText();

            var resultNode = objectMapper.readTree(jsonText);

            // Store Relational MC Action Plan Items
            var actionPlanNode = rootNode.path("candidates").get(0)
                    .path("content").path("parts").get(0).path("text");
            // Note: jsonText was already extracted above
            var actionPlanArray = resultNode.path("mcActionPlan");
            
            int newCount = 0;
            int updatedCount = 0;

            for (var itemNode : actionPlanArray) {
                String facility = itemNode.path("facility").asText();
                String action = itemNode.path("action").asText();
                String priority = itemNode.path("priority").asText();
                String timeline = itemNode.path("timeline").asText();
                String outcome = itemNode.path("expectedOutcome").asText();

                // SHA-256 Hashing for Deduplication: Core fields only
                String hashInput = surveyId + "|" + facility + "|" + action;
                String hash = com.finsight.backend.util.NormalizationUtils.generateHash(hashInput);

                var existing = actionItemRepository.findByHash(hash);
                SurveyActionItem item;
                if (existing.isPresent()) {
                    item = existing.get();
                    updatedCount++;
                } else {
                    item = new SurveyActionItem();
                    item.setSurveyId(surveyId);
                    item.setFacility(facility);
                    item.setAction(action);
                    item.setStatus("TODO");
                    item.setHash(hash);
                    newCount++;
                }
                
                item.setPriority(priority);
                item.setTimeline(timeline);
                item.setExpectedOutcome(outcome);
                item.setIsActive(true);
                actionItemRepository.save(item);
            }

            log.info("Relational MC Action Plan synced for surveyId: {}. New items: {}, Updated items: {}",
                    surveyId, newCount, updatedCount);

            // Also keep JSON in Survey for backward compatibility (optional but safer for existing UI)
            String actionPlanJson = objectMapper.writeValueAsString(actionPlanNode);
            surveyRepository.findById(surveyId).ifPresent(s -> {
                s.setActionPlan(actionPlanJson);
                surveyRepository.save(s);
            });

            // Also clear old insights to keep DB clean (legacy)
            insightRepository.deleteBySurveyId(surveyId);

            log.info("Relational MC Action Plan with {} items synced for surveyId: {}",
                    actionPlanNode.size(), surveyId);
        } else {
            log.error("Gemini API error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Gemini API returned error: " + response.statusCode());
        }
    }
}
