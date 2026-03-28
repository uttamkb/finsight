package com.finsight.backend.service.survey;

import com.finsight.backend.entity.survey.Survey;
import com.finsight.backend.repository.survey.SurveyRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class SurveySchedulerServiceTest {

    private final SurveyIngestionService ingestionService = Mockito.mock(SurveyIngestionService.class);
    private final SurveyRepository surveyRepository = Mockito.mock(SurveyRepository.class);

    private final SurveySchedulerService scheduler =
            new SurveySchedulerService(ingestionService, surveyRepository);

    @Test
    void syncResponses_syncsActiveSurveys() throws Exception {
        Survey survey = new Survey();
        survey.setId(1L);
        survey.setStatus("ACTIVE");

        when(surveyRepository.findAll()).thenReturn(List.of(survey));

        scheduler.syncResponses();

        verify(ingestionService, times(1)).syncResponses(1L);
    }

    @Test
    void syncResponses_skipsArchivedSurveys() throws Exception {
        Survey survey = new Survey();
        survey.setId(2L);
        survey.setStatus("ARCHIVED");

        when(surveyRepository.findAll()).thenReturn(List.of(survey));

        scheduler.syncResponses();

        verify(ingestionService, never()).syncResponses(anyLong());
    }

    @Test
    void syncResponses_handlesExceptionGracefully() throws Exception {
        Survey survey = new Survey();
        survey.setId(3L);
        survey.setStatus("ACTIVE");

        when(surveyRepository.findAll()).thenReturn(List.of(survey));
        doThrow(new RuntimeException("API error")).when(ingestionService).syncResponses(3L);

        // Should NOT propagate — errors are caught and logged
        scheduler.syncResponses();

        verify(ingestionService, times(1)).syncResponses(3L);
    }
}
