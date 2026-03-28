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
    void deleteBySurveyIdAndQuestion(Long surveyId, String question);
    
    boolean existsByHash(String hash);
    
    @org.springframework.data.jpa.repository.Query("SELECT r.hash FROM SurveyResponse r WHERE r.surveyId = :surveyId AND r.hash IS NOT NULL")
    java.util.Set<String> findAllHashesBySurveyId(@org.springframework.data.repository.query.Param("surveyId") Long surveyId);
}
