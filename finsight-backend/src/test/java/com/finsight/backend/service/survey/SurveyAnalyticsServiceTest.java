package com.finsight.backend.service.survey;

import com.finsight.backend.entity.survey.SurveyResponse;
import com.finsight.backend.repository.survey.SurveyResponseRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class SurveyAnalyticsServiceTest {

    private final SurveyResponseRepository responseRepository = Mockito.mock(SurveyResponseRepository.class);
    private final SurveyAnalyticsService service = new SurveyAnalyticsService(responseRepository);

    @Test
    void testGetAggregatedResults_ReturnsCorrectAverage() {
        Long surveyId = 1L;
        LocalDateTime ts = LocalDateTime.now();

        SurveyResponse r1 = new SurveyResponse();
        r1.setQuestion("Gym"); r1.setAnswer("4"); r1.setTimestamp(ts);

        SurveyResponse r2 = new SurveyResponse();
        r2.setQuestion("Gym"); r2.setAnswer("5"); r2.setTimestamp(ts.plusSeconds(1));

        when(responseRepository.findBySurveyId(surveyId)).thenReturn(List.of(r1, r2));

        Map<String, Object> result = service.getAggregatedResults(surveyId);

        assertNotNull(result);
        @SuppressWarnings("unchecked")
        Map<String, Double> ratings = (Map<String, Double>) result.get("averageRatings");
        assertEquals(4.5, ratings.get("Gym"), 0.01);
        assertEquals(1L, result.get("surveyId"));
    }

    @Test
    void testGetAggregatedResults_IgnoresTextResponses() {
        Long surveyId = 2L;
        LocalDateTime ts = LocalDateTime.now();

        SurveyResponse r1 = new SurveyResponse();
        r1.setQuestion("Additional Feedback"); r1.setAnswer("Great place!"); r1.setTimestamp(ts);

        when(responseRepository.findBySurveyId(surveyId)).thenReturn(List.of(r1));

        Map<String, Object> result = service.getAggregatedResults(surveyId);

        @SuppressWarnings("unchecked")
        Map<String, Double> ratings = (Map<String, Double>) result.get("averageRatings");
        assertTrue(ratings.isEmpty(), "Text answers should not appear in averageRatings");
    }

    @Test
    void testGetAggregatedResults_EmptyResponses() {
        Long surveyId = 3L;
        when(responseRepository.findBySurveyId(surveyId)).thenReturn(List.of());

        Map<String, Object> result = service.getAggregatedResults(surveyId);

        assertEquals(0L, result.get("totalResponses"));
    }
}
