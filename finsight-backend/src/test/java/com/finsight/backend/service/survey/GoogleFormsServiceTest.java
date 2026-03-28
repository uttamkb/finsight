package com.finsight.backend.service.survey;

import com.finsight.backend.client.GoogleDriveClient;
import com.finsight.backend.client.GoogleFormsClient;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyRepository;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import com.finsight.backend.service.AppConfigService;
import com.google.api.services.forms.v1.Forms;
import com.google.api.services.forms.v1.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GoogleFormsServiceTest {

    @Mock private GoogleFormsClient googleFormsClient;
    @Mock private GoogleDriveClient googleDriveClient;
    @Mock private AppConfigService appConfigService;
    @Mock private SurveyRepository surveyRepository;
    @Mock private SurveyResponseRepository surveyResponseRepository;

    private GoogleFormsService googleFormsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        googleFormsService = new GoogleFormsService(googleFormsClient, googleDriveClient,
                appConfigService, surveyRepository, surveyResponseRepository);
    }

    @Test
    void syncResponsesDirectly_successfulSync() throws Exception {
        // Arrange
        Long surveyId = 1L;
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setFormId("mock-form-id");
        survey.setTenantId("test-tenant");
        when(surveyRepository.findById(surveyId)).thenReturn(Optional.of(survey));

        AppConfig config = new AppConfig();
        config.setServiceAccountJson("{\"test\": \"json\"}");
        when(appConfigService.getConfig()).thenReturn(config);

        Forms formsService = mock(Forms.class, RETURNS_DEEP_STUBS);
        when(googleFormsClient.getFormsService(anyString())).thenReturn(formsService);

        // Mock Form mapping
        Form form = new Form();
        Item item = new Item()
                .setTitle("Security Rating")
                .setQuestionItem(new QuestionItem()
                        .setQuestion(new Question().setQuestionId("q1")));
        form.setItems(Collections.singletonList(item));
        when(formsService.forms().get(anyString()).execute()).thenReturn(form);

        // Mock Responses
        ListFormResponsesResponse listResponse = new ListFormResponsesResponse();
        FormResponse formResponse = new FormResponse();
        formResponse.setCreateTime(OffsetDateTime.now().toString());
        
        Answer answer = new Answer();
        TextAnswers textAnswers = new TextAnswers();
        textAnswers.setAnswers(Collections.singletonList(new TextAnswer().setValue("5")));
        answer.setTextAnswers(textAnswers);
        
        formResponse.setAnswers(Collections.singletonMap("q1", answer));
        listResponse.setResponses(Collections.singletonList(formResponse));
        
        when(formsService.forms().responses().list(anyString()).execute()).thenReturn(listResponse);
        when(surveyResponseRepository.existsBySurveyIdAndTimestampAndQuestion(anyLong(), any(), anyString()))
                .thenReturn(false);

        // Act
        googleFormsService.syncResponsesDirectly(surveyId);

        // Assert
        verify(surveyResponseRepository, atLeastOnce()).save(any());
    }

    @Test
    void createQuarterlySurvey_successfulCreation() throws Exception {
        // Arrange
        String tenantId = "tenant-123";
        AppConfig config = new AppConfig();
        config.setServiceAccountJson("mock-json");
        when(appConfigService.getConfig()).thenReturn(config);

        Forms formsService = mock(Forms.class, RETURNS_DEEP_STUBS);
        when(googleFormsClient.getFormsService(anyString())).thenReturn(formsService);
        when(googleDriveClient.getDriveService(anyString())).thenReturn(mock(com.google.api.services.drive.Drive.class));
        when(googleDriveClient.createFile(any(), anyString(), anyString())).thenReturn("new-form-id");
        
        when(formsService.forms().batchUpdate(anyString(), any()).execute()).thenReturn(new BatchUpdateFormResponse());
        
        Survey savedSurvey = new Survey();
        savedSurvey.setId(99L);
        when(surveyRepository.save(any())).thenReturn(savedSurvey);

        // Act
        Survey result = googleFormsService.createQuarterlySurvey(tenantId, "Q1", 2026);

        // Assert
        assertNotNull(result);
        verify(googleDriveClient).createFile(any(), contains("Q1 2026"), eq("application/vnd.google-apps.form"));
        verify(surveyRepository).save(any());
    }
}
