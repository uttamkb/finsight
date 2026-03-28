package com.finsight.backend.service.survey;

import com.finsight.backend.client.GoogleFormsClient;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.service.AppConfigService;
import com.google.api.services.forms.v1.Forms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Handles connecting to an externally-created Google Form by Form ID.
 * No form creation is done by the platform.
 */
@Service
public class SurveyConnectService {

    private static final Logger log = LoggerFactory.getLogger(SurveyConnectService.class);

    private final GoogleFormsClient googleFormsClient;
    private final AppConfigService appConfigService;
    private final SurveyRepository surveyRepository;

    public SurveyConnectService(GoogleFormsClient googleFormsClient,
                                AppConfigService appConfigService,
                                SurveyRepository surveyRepository) {
        this.googleFormsClient = googleFormsClient;
        this.appConfigService = appConfigService;
        this.surveyRepository = surveyRepository;
    }

    /**
     * Validates that the form is accessible and persists a Survey record linked to it.
     */
    public Survey connectForm(String tenantId, String formId, String label) throws Exception {
        AppConfig config = appConfigService.getConfig();
        String serviceAccountJson = config.getServiceAccountJson();

        if (serviceAccountJson == null || serviceAccountJson.trim().isEmpty()) {
            throw new com.finsight.backend.exception.BusinessException(
                "Google Service Account JSON is not configured. Please set up in Settings → Connectivity.");
        }

        // Validate form is accessible
        Forms formsService = googleFormsClient.getFormsService(serviceAccountJson);
        com.google.api.services.forms.v1.model.Form form;
        try {
            form = formsService.forms().get(formId).execute();
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            if (e.getStatusCode() == 403) {
                throw new com.finsight.backend.exception.BusinessException(
                    "Google API Error: Permission Denied (403). Please share the Google Form with the service account: " +
                    "finsight-sheet-reader@finsight-sheet-integration.iam.gserviceaccount.com as an Editor.");
            } else if (e.getStatusCode() == 404) {
                throw new com.finsight.backend.exception.BusinessException(
                    "Google API Error: Form not found (404). Please verify the Form ID is correct.");
            }
            throw e;
        }
        String formUrl = "https://docs.google.com/forms/d/" + formId + "/viewform";
        log.info("Successfully connected to Google Form: '{}' ({})", form.getInfo().getTitle(), formId);

        // Archive any existing ACTIVE surveys for this tenant
        surveyRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, "ACTIVE")
            .forEach(s -> {
                s.setStatus("ARCHIVED");
                surveyRepository.save(s);
            });

        // Persist survey record
        Survey survey = new Survey();
        survey.setTenantId(tenantId);
        survey.setFormId(formId);
        survey.setFormUrl(formUrl);
        survey.setLabel(label != null && !label.isBlank() ? label : form.getInfo().getTitle());
        survey.setStatus("ACTIVE");

        Survey saved = surveyRepository.save(survey);
        log.info("Survey connected and saved with ID: {}", saved.getId());
        return saved;
    }
}
