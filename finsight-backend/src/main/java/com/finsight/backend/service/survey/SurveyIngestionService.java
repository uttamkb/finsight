package com.finsight.backend.service.survey;

import com.finsight.backend.client.GoogleFormsClient;
import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import com.finsight.backend.service.AppConfigService;
import com.google.api.services.forms.v1.Forms;
import com.google.api.services.forms.v1.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles fetching and persisting responses from a connected Google Form.
 * Does NOT create forms — that is done externally by the user.
 */
@Service
public class SurveyIngestionService {

    private static final Logger log = LoggerFactory.getLogger(SurveyIngestionService.class);

    private final GoogleFormsService googleFormsService;
    private final GoogleSheetsSyncService googleSheetsSyncService;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository responseRepository;

    public SurveyIngestionService(GoogleFormsService googleFormsService,
                                 GoogleSheetsSyncService googleSheetsSyncService,
                                 SurveyRepository surveyRepository,
                                 SurveyResponseRepository responseRepository) {
        this.googleFormsService = googleFormsService;
        this.googleSheetsSyncService = googleSheetsSyncService;
        this.surveyRepository = surveyRepository;
        this.responseRepository = responseRepository;
    }

    @org.springframework.transaction.annotation.Transactional
    public void syncResponses(Long surveyId) throws Exception {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found: " + surveyId));

        // Use Google Sheets as Primary Source if linked, as Headers provide better mapping for Radar
        if (survey.getSheetId() != null && !survey.getSheetId().trim().isEmpty()) {
            log.info("Syncing surveyId: {} using Google Sheets: {}", surveyId, survey.getSheetId());
            googleSheetsSyncService.syncResponses(surveyId);
        } else {
            log.info("Syncing surveyId: {} using Google Forms API directly.", surveyId);
            // Self-healing: Delete any remnants of the "Unknown Question" bug for this survey
            responseRepository.deleteBySurveyIdAndQuestion(surveyId, "Unknown Question");
            googleFormsService.syncResponsesDirectly(surveyId);
        }
    }
}
