package com.finsight.backend.service.survey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.entity.survey.SurveyActionItem;
import com.finsight.backend.repository.survey.SurveyActionItemRepository;
import com.finsight.backend.repository.survey.SurveyInsightRepository;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.service.AppConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SurveyAiServiceTest {

    @Mock private AppConfigService appConfigService;
    @Mock private SurveyInsightRepository insightRepository;
    @Mock private SurveyRepository surveyRepository;
    @Mock private SurveyActionItemRepository actionItemRepository;
    @Mock private SurveyAnalyticsService analyticsService;
    @Mock private HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SurveyAiService aiService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        aiService = new SurveyAiService(appConfigService, insightRepository,
                surveyRepository, actionItemRepository, analyticsService,
                httpClient, objectMapper);
    }

    @Test
    void generateInsights_skipsWhenNoResponses() throws Exception {
        Long surveyId = 1L;
        when(analyticsService.buildGeminiContext(surveyId)).thenReturn("");

        aiService.generateInsights(surveyId);

        verify(analyticsService).buildGeminiContext(surveyId);
        // When context is blank, Gemini is NOT called
        verify(appConfigService, never()).getConfig();
        verify(surveyRepository, never()).findById(any());
    }

    @Test
    void generateInsights_throwsWhenGeminiKeyMissing() throws Exception {
        Long surveyId = 2L;
        when(analyticsService.buildGeminiContext(surveyId)).thenReturn("Avg Security: 3.5/5 | n=10");

        AppConfig config = new AppConfig();
        config.setGeminiApiKey(null);
        when(appConfigService.getConfig()).thenReturn(config);

        try {
            aiService.generateInsights(surveyId);
        } catch (IllegalStateException e) {
            // Expected: Gemini API Key not configured
            assert e.getMessage().contains("Gemini API Key");
        }

        verify(analyticsService).buildGeminiContext(surveyId);
        verify(appConfigService).getConfig();
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateInsights_successfulGeneration_updatesRelationalData() throws Exception {
        // Arrange
        Long surveyId = 100L;
        when(analyticsService.buildGeminiContext(surveyId)).thenReturn("Mock Data");
        
        AppConfig config = new AppConfig();
        config.setGeminiApiKey("test-key");
        when(appConfigService.getConfig()).thenReturn(config);

        String mockGeminiResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{
                        "text": "{\\"mcActionPlan\\": [{\\"facility\\":\\"Security\\",\\"action\\":\\"Improve CCTV\\",\\"priority\\":\\"HIGH\\",\\"timeline\\":\\"Short term\\",\\"expectedOutcome\\":\\"Safety\\"}]}"
                      }]
                    }
                  }]
                }
                """;
        
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockGeminiResponse);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        Survey mockSurvey = new Survey();
        mockSurvey.setId(surveyId);
        when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(mockSurvey));
        when(actionItemRepository.findBySurveyIdAndFacilityAndAction(eq(surveyId), anyString(), anyString()))
                .thenReturn(Optional.empty());

        // Act
        aiService.generateInsights(surveyId);

        // Assert
        verify(actionItemRepository).save(any(SurveyActionItem.class));
        verify(surveyRepository).save(mockSurvey);
        verify(insightRepository).deleteBySurveyId(surveyId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateInsights_apiError_throwsException() throws Exception {
        // Arrange
        Long surveyId = 101L;
        when(analyticsService.buildGeminiContext(surveyId)).thenReturn("Mock Data");
        
        AppConfig config = new AppConfig();
        config.setGeminiApiKey("test-key");
        when(appConfigService.getConfig()).thenReturn(config);

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("Internal Server Error");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> aiService.generateInsights(surveyId));
    }

    @Test
    @SuppressWarnings("unchecked")
    void generateInsights_existingActionItem_updatesItem() throws Exception {
        // Arrange
        Long surveyId = 200L;
        when(analyticsService.buildGeminiContext(surveyId)).thenReturn("Mock Data");
        
        AppConfig config = new AppConfig();
        config.setGeminiApiKey("test-key");
        when(appConfigService.getConfig()).thenReturn(config);

        String mockGeminiResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{
                        "text": "{\\"mcActionPlan\\": [{\\"facility\\":\\"Security\\",\\"action\\":\\"Improve CCTV\\",\\"priority\\":\\"HIGH\\",\\"timeline\\":\\"Short term\\",\\"expectedOutcome\\":\\"New Outcome\\"}]}"
                      }]
                    }
                  }]
                }
                """;
        
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(mockGeminiResponse);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        SurveyActionItem existingItem = new SurveyActionItem();
        existingItem.setFacility("Security");
        existingItem.setAction("Improve CCTV");
        when(actionItemRepository.findBySurveyIdAndFacilityAndAction(eq(surveyId), anyString(), anyString()))
                .thenReturn(Optional.of(existingItem));
        when(surveyRepository.findById(surveyId)).thenReturn(Optional.empty());

        // Act
        aiService.generateInsights(surveyId);

        // Assert
        verify(actionItemRepository).save(existingItem);
        assertEquals("New Outcome", existingItem.getExpectedOutcome());
    }
}
