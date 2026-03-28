package com.finsight.backend.service.survey;

import com.finsight.backend.client.GoogleDriveClient;
import com.finsight.backend.client.GoogleFormsClient;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import com.finsight.backend.service.AppConfigService;
import com.google.api.services.drive.Drive;
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
    private final GoogleDriveClient googleDriveClient;
    private final AppConfigService appConfigService;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;

    public GoogleFormsService(GoogleFormsClient googleFormsClient, GoogleDriveClient googleDriveClient,
                            AppConfigService appConfigService, 
                            SurveyRepository surveyRepository, SurveyResponseRepository surveyResponseRepository) {
        this.googleFormsClient = googleFormsClient;
        this.googleDriveClient = googleDriveClient;
        this.appConfigService = appConfigService;
        this.surveyRepository = surveyRepository;
        this.surveyResponseRepository = surveyResponseRepository;
    }

    public Survey createQuarterlySurvey(String tenantId, String quarter, int year) throws Exception {
        AppConfig config = appConfigService.getConfig();
        String serviceAccountJson = config.getServiceAccountJson();

        if (serviceAccountJson == null || serviceAccountJson.trim().isEmpty()) {
            throw new com.finsight.backend.exception.BusinessException("Google Service Account JSON is not configured in Base Configuration. Please setup in Settings -> Connectivity.");
        }

        Forms formsService = googleFormsClient.getFormsService(serviceAccountJson);
        Drive driveService = googleDriveClient.getDriveService(serviceAccountJson);
        
        String title = String.format("Resident Survey %s %d", quarter, year);
        log.info("Creating form via Drive API: '{}'", title);

        // 1. Create the Form as a File in Drive (Workaround for Service Account 500 error)
        String formId = googleDriveClient.createFile(driveService, title, "application/vnd.google-apps.form");
        log.info("Form file created in Drive with ID: {}", formId);

        // 2. Add Questions and set Title via BatchUpdate
        List<Request> requests = new ArrayList<>();
        int index = 0;

        // Update form info (Title)
        requests.add(new Request().setUpdateFormInfo(new UpdateFormInfoRequest()
            .setInfo(new Info().setTitle(title).setDescription("Quarterly feedback for Sowparnika Sanvi Phase 1 Facilities & Services"))
            .setUpdateMask("title,description")));

        // Section 1: General Info
        String[] infoQuestions = {"Resident Name", "Flat Number & Block"};
        for (String q : infoQuestions) {
            requests.add(new Request().setCreateItem(new CreateItemRequest()
                .setItem(new Item()
                    .setTitle(q)
                    .setQuestionItem(new QuestionItem()
                        .setQuestion(new Question()
                            .setRequired(true)
                            .setTextQuestion(new TextQuestion()))))
                .setLocation(new Location().setIndex(index++))));
        }

        // Section 2: Ratings (Scale 1-5)
        String[] ratingQuestions = {
            "Overall Security Service Satisfaction",
            "Visitor Verification (MyGate) Accuracy",
            "Premises Safety Confidence",
            "Housekeeping: Corridor & Common Area Cleanliness",
            "Garbage Collection Timing & Consistency",
            "Maintenance Response Time (Electrical/Plumbing)",
            "Quality of Maintenance Work (Resolution Quality)",
            "Facility Health (Gym, Club House, WTP, STP)",
            "Overall Living Experience Satisfaction"
        };

        for (String q : ratingQuestions) {
            requests.add(new Request().setCreateItem(new CreateItemRequest()
                .setItem(new Item()
                    .setTitle(q)
                    .setQuestionItem(new QuestionItem()
                        .setQuestion(new Question()
                            .setRequired(true)
                            .setScaleQuestion(new ScaleQuestion()
                                .setLow(1)
                                .setHigh(5)
                                .setLowLabel("Poor")
                                .setHighLabel("Excellent")))))
                .setLocation(new Location().setIndex(index++))));
        }

        // Section 3: Open Feedback
        requests.add(new Request().setCreateItem(new CreateItemRequest()
            .setItem(new Item()
                .setTitle("Specific Suggestions or Unresolved Issues")
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
    public void syncResponsesDirectly(Long surveyId) throws Exception {
        Survey survey = surveyRepository.findById(surveyId)
                .orElseThrow(() -> new IllegalArgumentException("Survey not found"));

        AppConfig config = appConfigService.getConfig();
        String serviceAccountJson = config.getServiceAccountJson();
        if (serviceAccountJson == null || serviceAccountJson.trim().isEmpty()) {
            throw new com.finsight.backend.exception.BusinessException("Google Service Account JSON is not configured.");
        }

        Forms formsService = googleFormsClient.getFormsService(serviceAccountJson);
        
        // 1. Get Form to map IDs to Titles
        Form form = formsService.forms().get(survey.getFormId()).execute();
        java.util.Map<String, String> questionMap = new java.util.HashMap<>();
        log.info("Inspecting Form structure for ID: {}", survey.getFormId());
        
        if (form.getItems() != null) {
            for (Item item : form.getItems()) {
                String itemTitle = item.getTitle();
                String itemId = item.getItemId();
                log.info("- Item: '{}' [ID: {}, Type: {}]", itemTitle, itemId, 
                    item.getQuestionItem() != null ? "Question" : (item.getQuestionGroupItem() != null ? "Group" : "Other"));
                
                String mappingTitle = (itemTitle != null && !itemTitle.isBlank()) ? itemTitle : "Untitled Question";
                
                // Map the Item ID
                questionMap.put(itemId, mappingTitle);
                
                // Case 1: Simple Question
                if (item.getQuestionItem() != null && item.getQuestionItem().getQuestion() != null) {
                    String qId = item.getQuestionItem().getQuestion().getQuestionId();
                    questionMap.put(qId, mappingTitle);
                    log.info("  Mapped Question ID: {} -> '{}'", qId, mappingTitle);
                }
                // Case 2: Question Group (e.g. Grids)
                else if (item.getQuestionGroupItem() != null) {
                    List<Question> groupQuestions = item.getQuestionGroupItem().getQuestions();
                    if (groupQuestions != null) {
                        for (Question q : groupQuestions) {
                            String subTitle = mappingTitle;
                            if (q.getRowQuestion() != null && q.getRowQuestion().getTitle() != null) {
                                subTitle = mappingTitle + ": " + q.getRowQuestion().getTitle();
                            }
                            questionMap.put(q.getQuestionId(), subTitle);
                            log.info("  Mapped Group Question ID: {} -> '{}'", q.getQuestionId(), subTitle);
                        }
                    }
                }
            }
        }
        log.info("Populated question map with {} ID mappings.", questionMap.size());

        // 2. Fetch Responses
        ListFormResponsesResponse response = formsService.forms().responses().list(survey.getFormId()).execute();
        List<FormResponse> responses = response.getResponses();
        log.info("Found {} total responses in Google Forms.", (responses != null ? responses.size() : 0));

        if (responses == null || responses.isEmpty()) {
            log.info("No responses found for form: {}", survey.getFormId());
            return;
        }

        for (FormResponse fr : responses) {
            java.time.OffsetDateTime createTime = java.time.OffsetDateTime.parse(fr.getCreateTime());
            java.time.LocalDateTime timestamp = createTime.toLocalDateTime();

            java.util.Map<String, Answer> answers = fr.getAnswers();
            if (answers == null) continue;

            for (java.util.Map.Entry<String, Answer> entry : answers.entrySet()) {
                String questionId = entry.getKey();
                String questionTitle = questionMap.get(questionId);
                
                // Fallback logic: If mapping fails, use questionId to prevent collision
                if (questionTitle == null || questionTitle.isBlank() || questionTitle.equals("Unknown Question")) {
                    questionTitle = "Q-" + questionId;
                }
                
                Answer answerObj = entry.getValue();
                String answerText = "";
                if (answerObj.getTextAnswers() != null && !answerObj.getTextAnswers().getAnswers().isEmpty()) {
                    answerText = answerObj.getTextAnswers().getAnswers().get(0).getValue();
                } else if (answerObj.getFileUploadAnswers() != null) {
                    answerText = "[File Upload]";
                } else {
                    log.warn("  Answer for '{}' (ID: {}) has NO text value. Raw: {}", 
                        questionTitle, questionId, answerObj.toString());
                }

                log.info("  -> Data: '{}' = '{}'", questionTitle, answerText);

                // Idempotency check (now safe with unique titles)
                if (!surveyResponseRepository.existsBySurveyIdAndTimestampAndQuestion(surveyId, timestamp, questionTitle)) {
                    com.finsight.backend.entity.survey.SurveyResponse sr = new com.finsight.backend.entity.survey.SurveyResponse();
                    sr.setSurveyId(surveyId);
                    sr.setTenantId(survey.getTenantId());
                    sr.setTimestamp(timestamp);
                    sr.setQuestion(questionTitle);
                    sr.setAnswer(answerText);
                    surveyResponseRepository.save(sr);
                }
            }
        }
        log.info("Synced {} responses for survey: {}", responses.size(), surveyId);
    }
}
