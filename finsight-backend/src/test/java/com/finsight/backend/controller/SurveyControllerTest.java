package com.finsight.backend.controller;

import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyInsightRepository;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import com.finsight.backend.service.survey.GoogleFormsService;
import com.finsight.backend.service.survey.GoogleSheetsSyncService;
import com.finsight.backend.service.survey.SurveyAiService;
import com.finsight.backend.service.survey.SurveyAnalyticsService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class SurveyControllerTest {

    private final GoogleFormsService formsService = Mockito.mock(GoogleFormsService.class);
    private final GoogleSheetsSyncService syncService = Mockito.mock(GoogleSheetsSyncService.class);
    private final SurveyAiService aiService = Mockito.mock(SurveyAiService.class);
    private final SurveyAnalyticsService analyticsService = Mockito.mock(SurveyAnalyticsService.class);
    private final SurveyRepository surveyRepository = Mockito.mock(SurveyRepository.class);
    private final SurveyResponseRepository responseRepository = Mockito.mock(SurveyResponseRepository.class);
    private final SurveyInsightRepository insightRepository = Mockito.mock(SurveyInsightRepository.class);

    private final SurveyController controller = new SurveyController(
            formsService, syncService, aiService, analyticsService, 
            surveyRepository, responseRepository, insightRepository
    );

    @Test
    void testGetDashboardData_IncludesExecutiveSummary() {
        Long surveyId = 1L;
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setExecutiveSummary("Test Summary");

        Map<String, Object> mockData = new HashMap<>();
        mockData.put("totalResponses", 10);

        when(analyticsService.getAggregatedResults(surveyId)).thenReturn(mockData);
        when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));
        when(insightRepository.findBySurveyId(surveyId)).thenReturn(java.util.List.of());

        Map<String, Object> result = controller.getDashboardData(surveyId);

        assertTrue(result.containsKey("executiveSummary"));
        assertEquals("Test Summary", result.get("executiveSummary"));
        assertEquals(10, result.get("totalResponses"));
    }
}
