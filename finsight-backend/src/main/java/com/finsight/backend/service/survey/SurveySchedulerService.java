package com.finsight.backend.service.survey;

import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class SurveySchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SurveySchedulerService.class);
    private final GoogleFormsService formsService;
    private final GoogleSheetsSyncService syncService;
    private final SurveyAiService aiService;
    private final SurveyRepository surveyRepository;

    public SurveySchedulerService(GoogleFormsService formsService, GoogleSheetsSyncService syncService, 
                                  SurveyAiService aiService, SurveyRepository surveyRepository) {
        this.formsService = formsService;
        this.syncService = syncService;
        this.aiService = aiService;
        this.surveyRepository = surveyRepository;
    }

    // Every hour
    @Scheduled(cron = "0 0 * * * *")
    public void syncResponses() {
        log.info("Starting scheduled survey response sync...");
        List<Survey> activeSurveys = surveyRepository.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .toList();

        for (Survey survey : activeSurveys) {
            try {
                syncService.syncResponses(survey.getId());
                aiService.generateInsights(survey.getId());
            } catch (Exception e) {
                log.error("Failed to sync survey {}: {}", survey.getId(), e.getMessage());
            }
        }
    }

    // Every 1st of Jan, Apr, Jul, Oct at 00:00
    @Scheduled(cron = "0 0 0 1 1,4,7,10 *")
    public void createQuarterlySurvey() {
        log.info("Starting scheduled quarterly survey creation...");
        
        LocalDate now = LocalDate.now();
        String quarter = getQuarter(now.getMonthValue());
        int year = now.getYear();

        // Close previous surveys
        List<Survey> activeSurveys = surveyRepository.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .toList();
        
        for (Survey s : activeSurveys) {
            s.setStatus("CLOSED");
            surveyRepository.save(s);
        }

        // Ideally we would trigger this for each tenant, for now we assume a default tenant for the Association
        try {
            formsService.createQuarterlySurvey("local_tenant", quarter, year);
        } catch (Exception e) {
            log.error("Failed to create quarterly survey: {}", e.getMessage());
        }
    }

    private String getQuarter(int month) {
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }
}
