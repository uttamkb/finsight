package com.finsight.backend.service.survey;

import com.finsight.backend.client.GoogleSheetsClient;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.entity.survey.SurveyResponse;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import com.finsight.backend.service.AppConfigService;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class GoogleSheetsSyncService {

    private static final Logger log = LoggerFactory.getLogger(GoogleSheetsSyncService.class);
    private final GoogleSheetsClient googleSheetsClient;
    private final AppConfigService appConfigService;
    private final SurveyResponseRepository responseRepository;
    private final SurveyRepository surveyRepository;

    public GoogleSheetsSyncService(GoogleSheetsClient googleSheetsClient, AppConfigService appConfigService, 
                                 SurveyResponseRepository responseRepository, SurveyRepository surveyRepository) {
        this.googleSheetsClient = googleSheetsClient;
        this.appConfigService = appConfigService;
        this.responseRepository = responseRepository;
        this.surveyRepository = surveyRepository;
    }

    public void syncResponses(Long surveyId) throws Exception {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found"));

        if (survey.getSheetId() == null || survey.getSheetId().trim().isEmpty()) {
            log.warn("No Sheet ID linked for survey: {}", surveyId);
            return;
        }

        AppConfig config = appConfigService.getConfig();
        Sheets sheetsService = googleSheetsClient.getSheetsService(config.getServiceAccountJson());

        // Assuming responses are in the first sheet, starting from A2 (skipping header)
        String range = "Form Responses 1!A2:Z"; 
        ValueRange response = sheetsService.spreadsheets().values()
                .get(survey.getSheetId(), range)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            log.info("No new responses found for survey: {}", surveyId);
            return;
        }

        // Get headers to map questions
        ValueRange headerResponse = sheetsService.spreadsheets().values()
                .get(survey.getSheetId(), "Form Responses 1!A1:Z1")
                .execute();
        List<Object> headers = headerResponse.getValues().get(0);

        for (List<Object> row : values) {
            if (row.isEmpty()) continue;

            String timestampStr = row.get(0).toString();
            LocalDateTime timestamp = parseTimestamp(timestampStr);

            // Each row col (starting from 1) is an answer to the question in headers[col]
            for (int i = 1; i < row.size(); i++) {
                String question = headers.get(i).toString();
                String answer = row.get(i).toString();

                // Idempotency check
                if (!responseRepository.existsBySurveyIdAndTimestampAndQuestion(surveyId, timestamp, question)) {
                    SurveyResponse sr = new SurveyResponse();
                    sr.setSurveyId(surveyId);
                    sr.setTenantId(survey.getTenantId());
                    sr.setTimestamp(timestamp);
                    sr.setQuestion(question);
                    sr.setAnswer(answer);
                    responseRepository.save(sr);
                }
            }
        }
        log.info("Synced {} rows for survey: {}", values.size(), surveyId);
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        // Google Sheets typically uses "MM/dd/yyyy HH:mm:ss" or similar
        try {
            return LocalDateTime.parse(timestampStr, DateTimeFormatter.ofPattern("M/d/yyyy H:mm:ss"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(timestampStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e2) {
                return LocalDateTime.now(); // Fallback
            }
        }
    }
}
