package com.finsight.backend.service.survey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.survey.SurveyInsight;
import com.finsight.backend.entity.survey.SurveyResponse;
import com.finsight.backend.repository.survey.SurveyInsightRepository;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
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
import java.util.stream.Collectors;

@Service
public class SurveyAiService {

    private static final Logger log = LoggerFactory.getLogger(SurveyAiService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private final AppConfigService appConfigService;
    private final SurveyResponseRepository responseRepository;
    private final SurveyInsightRepository insightRepository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SurveyAiService(AppConfigService appConfigService, SurveyResponseRepository responseRepository, SurveyInsightRepository insightRepository) {
        this.appConfigService = appConfigService;
        this.responseRepository = responseRepository;
        this.insightRepository = insightRepository;
        this.httpClient = HttpClient.newBuilder().build();
        this.objectMapper = new ObjectMapper();
    }

    public void generateInsights(Long surveyId) throws Exception {
        List<SurveyResponse> responses = responseRepository.findBySurveyId(surveyId);
        if (responses.isEmpty()) return;

        AppConfig config = appConfigService.getConfig();
        String apiKey = config.getGeminiApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Gemini API Key is not configured.");
        }

        // Group responses by question/facility for context
        String context = responses.stream()
                .map(r -> String.format("Q: %s | A: %s", r.getQuestion(), r.getAnswer()))
                .collect(Collectors.joining("\n"));

        String prompt = """
                Analyze the following resident feedback for an apartment complex.
                Generate facility-wise insights including a sentiment score (0.0 to 1.0), 
                a brief summary of feedback, and actionable operational recommendations.
                
                Facilities to analyze: WTP, STP, Gym, DG Backup, Clubhouse, MyGate/Visitor Management, Maintenance, Security, Cleanliness.
                
                Return the result strictly as a JSON array of objects:
                [
                  {
                    "facility": "Facility Name",
                    "sentimentScore": 0.85,
                    "aiSummary": "Summary text",
                    "recommendations": "Recommendation text"
                  }
                ]
                
                Feedback Data:
                """ + context;

        String requestBody = String.format("""
            {
              "contents": [{
                "parts": [{"text": "%s"}]
              }],
              "generationConfig": {
                "temperature": 0.2,
                "responseMimeType": "application/json"
              }
            }
            """, escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            var rootNode = objectMapper.readTree(response.body());
            String jsonTextContent = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            
            List<Map<String, Object>> insightsList = objectMapper.readValue(jsonTextContent, List.class);
            
            for (Map<String, Object> insightMap : insightsList) {
                SurveyInsight insight = new SurveyInsight();
                insight.setSurveyId(surveyId);
                insight.setTenantId(responses.get(0).getTenantId());
                insight.setFacility((String) insightMap.get("facility"));
                insight.setSentimentScore(Double.valueOf(insightMap.get("sentimentScore").toString()));
                insight.setAiSummary((String) insightMap.get("aiSummary"));
                insight.setRecommendations((String) insightMap.get("recommendations"));
                insightRepository.save(insight);
            }
        }
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
