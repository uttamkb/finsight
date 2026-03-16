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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/survey")
@CrossOrigin(origins = "*")
@Tag(name = "Quarterly Surveys", description = "Endpoints for managing automated Google Forms surveys and collecting vendor feedback")
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
    @Operation(summary = "Create Quarterly Survey", description = "Generates a new Google Form targeted at vendor feedback for current tenant context. Automatically archives previous active surveys.")
    public Survey createSurvey(
            @Parameter(description = "Tenant ID") @RequestParam String tenantId, 
            @Parameter(description = "Financial quarter, e.g., Q1") @RequestParam String quarter, 
            @Parameter(description = "Current year") @RequestParam int year) throws Exception {
        
        // Archive existing active surveys for this tenant
        surveyRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "ACTIVE")
                .forEach(s -> {
                    s.setStatus("ARCHIVED");
                    surveyRepository.save(s);
                });

        return formsService.createQuarterlySurvey(tenantId, quarter, year);
    }

    @GetMapping("/current")
    @Operation(summary = "Get Current Active Survey", description = "Checks system constraints and outputs the actively tracked survey.")
    public Survey getCurrentSurvey(@Parameter(description = "Tenant ID") @RequestParam String tenantId) {
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
    @Operation(summary = "Sync Survey Responses", description = "Pulls in new Google Forms submitted responses sequentially into standard DB formats.")
    public String syncAndAnalyze(@Parameter(description = "Target Survey ID") @RequestParam Long surveyId) throws Exception {
        syncService.syncResponses(surveyId);
        aiService.generateInsights(surveyId);
        return "Sync and AI analysis completed successfully.";
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboardData(@RequestParam Long surveyId) {
        Map<String, Object> data = analyticsService.getAggregatedResults(surveyId);
        data.put("insights", insightRepository.findBySurveyId(surveyId));
        surveyRepository.findById(surveyId).ifPresent(s -> data.put("executiveSummary", s.getExecutiveSummary()));
        return data;
    }
}
