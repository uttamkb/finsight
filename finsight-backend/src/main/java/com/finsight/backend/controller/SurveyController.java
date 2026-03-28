package com.finsight.backend.controller;

import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.entity.survey.SurveyActionItem;
import com.finsight.backend.repository.survey.SurveyActionItemRepository;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import com.finsight.backend.service.survey.SurveyAiService;
import com.finsight.backend.service.survey.SurveyAnalyticsService;
import com.finsight.backend.service.survey.SurveyConnectService;
import com.finsight.backend.service.survey.SurveyIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/survey")
@CrossOrigin(origins = "*")
@Tag(name = "Resident Feedback", description = "Connect to Google Forms and run AI-powered MC Action Plan analysis")
public class SurveyController {

    private final SurveyConnectService connectService;
    private final SurveyIngestionService ingestionService;
    private final SurveyAiService aiService;
    private final SurveyAnalyticsService analyticsService;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;
    private final SurveyActionItemRepository actionItemRepository;

    public SurveyController(SurveyConnectService connectService,
                            SurveyIngestionService ingestionService,
                            SurveyAiService aiService,
                            SurveyAnalyticsService analyticsService,
                            SurveyRepository surveyRepository,
                            SurveyResponseRepository responseRepository,
                            SurveyActionItemRepository actionItemRepository) {
        this.connectService = connectService;
        this.ingestionService = ingestionService;
        this.aiService = aiService;
        this.analyticsService = analyticsService;
        this.surveyRepository = surveyRepository;
        this.responseRepository = responseRepository;
        this.actionItemRepository = actionItemRepository;
    }

    /**
     * Connect to an externally-created Google Form by Form ID.
     */
    @PostMapping("/connect")
    @Operation(summary = "Connect Google Form",
            description = "Links an existing Google Form (created by the user) to the platform by Form ID. Validates access and saves the survey record.")
    public Survey connectForm(
            @Parameter(description = "Tenant ID") @RequestParam String tenantId,
            @Parameter(description = "Google Form ID (from the form URL)") @RequestParam String formId,
            @Parameter(description = "Display label, e.g. Q1 2026 Resident Survey") @RequestParam(required = false) String label)
            throws Exception {
        return connectService.connectForm(tenantId, formId, label);
    }

    @GetMapping("/current")
    @Operation(summary = "Get Current Active Survey")
    public Survey getCurrentSurvey(@RequestParam String tenantId) {
        return surveyRepository.findFirstByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "ACTIVE")
                .orElse(null);
    }

    @GetMapping("/all")
    @Operation(summary = "List all surveys for tenant")
    public List<Survey> getAllSurveys(@RequestParam String tenantId) {
        return surveyRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "ACTIVE");
    }

    /**
     * Fetch latest responses AND run AI analysis to regenerate the MC Action Plan.
     */
    @PostMapping("/sync")
    @Operation(summary = "Sync Responses & Generate MC Action Plan",
            description = "Pulls new Google Form responses and runs Gemini AI to generate the MC Action Plan.")
    public Map<String, Object> syncAndAnalyze(
            @Parameter(description = "Survey ID") @RequestParam Long surveyId) throws Exception {
        ingestionService.syncResponses(surveyId);
        aiService.generateInsights(surveyId);
        long responseCount = responseRepository.findBySurveyId(surveyId).size();
        return Map.of("status", "ok", "message", "Sync and MC Action Plan generation complete.",
                "totalResponses", responseCount);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get Dashboard Data",
            description = "Returns aggregated ratings, total response count, and the persistent MC Action Plan.")
    public Map<String, Object> getDashboardData(@RequestParam Long surveyId) {
        Map<String, Object> data = new LinkedHashMap<>(analyticsService.getAggregatedResults(surveyId));

        surveyRepository.findById(surveyId).ifPresent(s -> {
            data.put("label", s.getLabel());
            data.put("formUrl", s.getFormUrl());
        });

        // Always return mcActionPlan from the new relational table
        List<SurveyActionItem> items = actionItemRepository.findBySurveyId(surveyId);
        data.put("mcActionPlan", items);

        return data;
    }

    @PatchMapping("/action-items/{id}")
    @Operation(summary = "Update Action Item Status",
            description = "Allows MC members to mark progress (TODO, IN_PROGRESS, DONE, NOT_FEASIBLE).")
    public SurveyActionItem updateActionItemStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        SurveyActionItem item = actionItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Action Item not found: " + id));
        item.setStatus(status);
        return actionItemRepository.save(item);
    }

    @GetMapping("/responses")
    public java.util.List<com.finsight.backend.entity.survey.SurveyResponse> getResponses(@RequestParam Long surveyId) {
        return responseRepository.findBySurveyId(surveyId);
    }
}
