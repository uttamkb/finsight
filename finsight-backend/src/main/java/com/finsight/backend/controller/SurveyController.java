package com.finsight.backend.controller;

import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.entity.survey.SurveyInsight;
import com.finsight.backend.entity.survey.SurveyResponse;
import com.finsight.backend.repository.survey.SurveyInsightRepository;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import com.finsight.backend.service.survey.GoogleFormsService;
import com.finsight.backend.service.survey.GoogleSheetsSyncService;
import com.finsight.backend.service.survey.SurveyAiService;
import com.finsight.backend.service.survey.SurveyAnalyticsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/survey")
@CrossOrigin(origins = "*")
public class SurveyController {

    private final GoogleFormsService formsService;
    private final GoogleSheetsSyncService syncService;
    private final SurveyAiService aiService;
    private final SurveyAnalyticsService analyticsService;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;
    private final SurveyInsightRepository insightRepository;

    public SurveyController(GoogleFormsService formsService, GoogleSheetsSyncService syncService, 
                            SurveyAiService aiService, SurveyAnalyticsService analyticsService, 
                            SurveyRepository surveyRepository, SurveyResponseRepository responseRepository, 
                            SurveyInsightRepository insightRepository) {
        this.formsService = formsService;
        this.syncService = syncService;
        this.aiService = aiService;
        this.analyticsService = analyticsService;
        this.surveyRepository = surveyRepository;
        this.responseRepository = responseRepository;
        this.insightRepository = insightRepository;
    }

    @PostMapping("/create")
    public Survey createSurvey(@RequestParam String tenantId, @RequestParam String quarter, @RequestParam int year) throws Exception {
        return formsService.createQuarterlySurvey(tenantId, quarter, year);
    }

    @GetMapping("/current")
    public Survey getCurrentSurvey(@RequestParam String tenantId) {
        return surveyRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "ACTIVE")
                .orElse(null);
    }

    @GetMapping("/responses")
    public List<SurveyResponse> getResponses(@RequestParam Long surveyId) {
        return responseRepository.findBySurveyId(surveyId);
    }

    @GetMapping("/insights")
    public List<SurveyInsight> getInsights(@RequestParam Long surveyId) {
        return insightRepository.findBySurveyId(surveyId);
    }

    @PostMapping("/sync")
    public String syncAndAnalyze(@RequestParam Long surveyId) throws Exception {
        syncService.syncResponses(surveyId);
        aiService.generateInsights(surveyId);
        return "Sync and AI analysis completed successfully.";
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboardData(@RequestParam Long surveyId) {
        Map<String, Object> data = analyticsService.getAggregatedResults(surveyId);
        data.put("insights", insightRepository.findBySurveyId(surveyId));
        return data;
    }
}
