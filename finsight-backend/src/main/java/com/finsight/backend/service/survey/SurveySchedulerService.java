package com.finsight.backend.service.survey;

import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SurveySchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SurveySchedulerService.class);
    private final SurveyIngestionService ingestionService;
    private final SurveyRepository surveyRepository;

    public SurveySchedulerService(SurveyIngestionService ingestionService,
                                  SurveyRepository surveyRepository) {
        this.ingestionService = ingestionService;
        this.surveyRepository = surveyRepository;
    }

    /**
     * Every hour: pull new responses for all active surveys.
     * AI analysis is triggered manually via POST /survey/sync.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void syncResponses() {
        log.info("Starting scheduled survey response sync...");
        List<Survey> activeSurveys = surveyRepository.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .toList();

        for (Survey survey : activeSurveys) {
            try {
                ingestionService.syncResponses(survey.getId());
                log.info("Hourly sync complete for surveyId: {}", survey.getId());
            } catch (Exception e) {
                log.error("Failed to sync survey {}: {}", survey.getId(), e.getMessage());
            }
        }
    }
}
