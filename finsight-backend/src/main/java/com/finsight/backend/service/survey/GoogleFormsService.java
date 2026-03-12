package com.finsight.backend.service.survey;

import com.finsight.backend.client.GoogleFormsClient;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.service.AppConfigService;
import com.google.api.services.forms.v1.Forms;
import com.google.api.services.forms.v1.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class GoogleFormsService {

    private static final Logger log = LoggerFactory.getLogger(GoogleFormsService.class);
    private final GoogleFormsClient googleFormsClient;
    private final AppConfigService appConfigService;
    private final SurveyRepository surveyRepository;

    public GoogleFormsService(GoogleFormsClient googleFormsClient, AppConfigService appConfigService, SurveyRepository surveyRepository) {
        this.googleFormsClient = googleFormsClient;
        this.appConfigService = appConfigService;
        this.surveyRepository = surveyRepository;
    }

    public Survey createQuarterlySurvey(String tenantId, String quarter, int year) throws Exception {
        AppConfig config = appConfigService.getConfig();
        String serviceAccountJson = config.getServiceAccountJson();

        if (serviceAccountJson == null || serviceAccountJson.trim().isEmpty()) {
            throw new IllegalStateException("Service Account JSON is not configured.");
        }

        Forms formsService = googleFormsClient.getFormsService(serviceAccountJson);

        // 1. Create the Form
        Form form = new Form();
        form.setInfo(new Info());
        form.getInfo().setTitle(String.format("Resident Satisfaction Survey - %s %d", quarter, year));
        form.getInfo().setDocumentTitle(String.format("Finsight_Survey_%s_%d_%s", tenantId, quarter, year));

        form = formsService.forms().create(form).execute();
        String formId = form.getFormId();

        // 2. Add Questions
        List<Request> requests = new ArrayList<>();
        
        String[] ratingFacilities = {
            "Water Treatment Plant (WTP)",
            "Sewage Treatment Plant (STP)",
            "Gym",
            "DG Power Backup",
            "Club House",
            "MyGate App / Visitor Management",
            "Maintenance Staff",
            "Security Staff",
            "Cleanliness",
            "Overall Community Satisfaction"
        };

        int index = 0;
        for (String facility : ratingFacilities) {
            requests.add(new Request().setCreateItem(new CreateItemRequest()
                .setItem(new Item()
                    .setTitle(facility)
                    .setQuestionItem(new QuestionItem()
                        .setQuestion(new Question()
                            .setRequired(true)
                            .setScaleQuestion(new ScaleQuestion()
                                .setLow(1)
                                .setHigh(5)
                                .setLowLabel("Very Dissatisfied")
                                .setHighLabel("Very Satisfied")))))
                .setLocation(new Location().setIndex(index++))));
        }

        // Add Text Feedback
        requests.add(new Request().setCreateItem(new CreateItemRequest()
            .setItem(new Item()
                .setTitle("Additional Feedback / Suggestions")
                .setQuestionItem(new QuestionItem()
                    .setQuestion(new Question()
                        .setTextQuestion(new TextQuestion().setParagraph(true)))))
            .setLocation(new Location().setIndex(index++))));

        BatchUpdateFormRequest batchUpdateRequest = new BatchUpdateFormRequest();
        batchUpdateRequest.setRequests(requests);
        formsService.forms().batchUpdate(formId, batchUpdateRequest).execute();

        // 3. Store Metadata
        Survey survey = new Survey();
        survey.setTenantId(tenantId);
        survey.setFormId(formId);
        survey.setFormUrl("https://docs.google.com/forms/d/" + formId + "/viewform");
        survey.setQuarter(quarter);
        survey.setYear(year);
        survey.setStatus("ACTIVE");
        
        return surveyRepository.save(survey);
    }
}
