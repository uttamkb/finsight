package com.finsight.backend.controller;

import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyActionItemRepository;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import com.finsight.backend.service.survey.SurveyAiService;
import com.finsight.backend.service.survey.SurveyAnalyticsService;
import com.finsight.backend.service.survey.SurveyConnectService;
import com.finsight.backend.service.survey.SurveyIngestionService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class SurveyControllerTest {

    private final SurveyConnectService connectService   = Mockito.mock(SurveyConnectService.class);
    private final SurveyIngestionService ingestionService = Mockito.mock(SurveyIngestionService.class);
    private final SurveyAiService aiService             = Mockito.mock(SurveyAiService.class);
    private final SurveyAnalyticsService analyticsService = Mockito.mock(SurveyAnalyticsService.class);
    private final SurveyRepository surveyRepository     = Mockito.mock(SurveyRepository.class);
    private final SurveyResponseRepository responseRepo = Mockito.mock(SurveyResponseRepository.class);
    private final SurveyActionItemRepository actionItemRepo = Mockito.mock(SurveyActionItemRepository.class);

    private final SurveyController controller = new SurveyController(
            connectService, ingestionService, aiService, analyticsService,
            surveyRepository, responseRepo, actionItemRepo
    );

    @Test
    void getDashboardData_returnsMcActionPlanAndTotalResponses() {
        Long surveyId = 1L;
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setLabel("Q1 2026 Survey");
        survey.setFormUrl("https://forms.google.com/test");
        survey.setActionPlan(null); // no plan yet → expect empty list

        Map<String, Object> mockData = new HashMap<>();
        mockData.put("totalResponses", 15);
        mockData.put("averageRatings", Map.of("Security", 3.5));

        when(analyticsService.getAggregatedResults(surveyId)).thenReturn(mockData);
        when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));

        Map<String, Object> result = controller.getDashboardData(surveyId);

        assertEquals(15, result.get("totalResponses"));
        assertTrue(result.containsKey("mcActionPlan"));
        assertTrue(result.containsKey("label"));
        assertEquals("Q1 2026 Survey", result.get("label"));
        // When actionPlan is null, mcActionPlan should be an empty list
        assertEquals(List.of(), result.get("mcActionPlan"));
    }

    @Test
    void getCurrentSurvey_returnsNullWhenNoneActive() {
        when(surveyRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc("local_tenant", "ACTIVE"))
                .thenReturn(Optional.empty());

        Survey result = controller.getCurrentSurvey("local_tenant");
        assertEquals(null, result);
    }
}
