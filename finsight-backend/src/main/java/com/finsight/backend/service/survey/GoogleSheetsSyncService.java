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

    @org.springframework.transaction.annotation.Transactional
    public void syncResponses(Long surveyId) throws Exception {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found"));

        if (survey.getSheetId() == null || survey.getSheetId().trim().isEmpty()) {
            log.warn("No Sheet ID linked for survey: {}", surveyId);
            return;
        }

        // Fetch existing hashes to avoid duplicates (preserving audit history)
        java.util.Set<String> existingHashes = responseRepository.findAllHashesBySurveyId(surveyId);
        log.info("Loaded {} existing response hashes for surveyId: {}", existingHashes.size(), surveyId);

        AppConfig config = appConfigService.getConfig();
        String serviceAccountJson = config.getServiceAccountJson();
        if (serviceAccountJson == null || serviceAccountJson.trim().isEmpty()) {
            throw new com.finsight.backend.exception.BusinessException("Google Service Account JSON is not configured.");
        }
        Sheets sheetsService = googleSheetsClient.getSheetsService(serviceAccountJson);
        
        // 0. Get the first sheet name dynamically
        com.google.api.services.sheets.v4.model.Spreadsheet spreadsheet = sheetsService.spreadsheets()
                .get(survey.getSheetId()).execute();
        String sheetName = spreadsheet.getSheets().get(0).getProperties().getTitle();

        // 1. Get and normalize headers
        ValueRange headerResponse = sheetsService.spreadsheets().values()
                .get(survey.getSheetId(), "'" + sheetName + "'!A1:Z1")
                .execute();
        List<Object> rawHeaders = headerResponse.getValues().get(0);
        java.util.List<String> normalizedHeaders = rawHeaders.stream()
                .map(h -> com.finsight.backend.util.NormalizationUtils.normalizeHeader(h.toString()))
                .collect(java.util.stream.Collectors.toList());

        // 2. Fetch responses skipping header
        String range = "'" + sheetName + "'!A2:Z"; 
        ValueRange response = sheetsService.spreadsheets().values()
                .get(survey.getSheetId(), range)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return;

        int newSaved = 0;
        for (List<Object> row : values) {
            if (row.isEmpty()) continue;

            String responseId = row.get(0).toString(); // Timestamp from column A acts as responseId
            LocalDateTime timestamp = parseTimestamp(responseId);

            for (int i = 1; i < row.size(); i++) {
                String rawAnswer = row.get(i).toString();
                String normalizedQuestion = normalizedHeaders.get(i);
                String normalizedAnswer = com.finsight.backend.util.NormalizationUtils.normalizeAnswer(rawAnswer);

                // Row-Hash Idempotency: SHA256(surveyId + responseId + normalizedQuestion + normalizedAnswer)
                String hashInput = surveyId + "|" + responseId + "|" + normalizedQuestion + "|" + normalizedAnswer;
                String hash = com.finsight.backend.util.NormalizationUtils.generateHash(hashInput);

                if (!existingHashes.contains(hash)) {
                    SurveyResponse sr = new SurveyResponse();
                    sr.setSurveyId(surveyId);
                    sr.setTenantId(survey.getTenantId());
                    sr.setTimestamp(timestamp);
                    sr.setQuestion(rawHeaders.get(i).toString()); // Keep raw for display
                    sr.setAnswer(rawAnswer);                      // Keep raw for display
                    sr.setHash(hash);
                    sr.setIsActive(true);
                    responseRepository.save(sr);
                    existingHashes.add(hash); // Add to local set to prevent duplicate rows in same sync
                    newSaved++;
                }
            }
        }
        log.info("Synced survey: {}. Found {} total rows, saved {} new response items.", 
                surveyId, values.size(), newSaved);
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
