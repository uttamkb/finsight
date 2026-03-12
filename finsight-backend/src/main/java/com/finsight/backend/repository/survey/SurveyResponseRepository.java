package com.finsight.backend.repository.survey;

import com.finsight.backend.entity.survey.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {
    List<SurveyResponse> findBySurveyId(Long surveyId);
    boolean existsBySurveyIdAndTimestampAndQuestion(Long surveyId, LocalDateTime timestamp, String question);
}
