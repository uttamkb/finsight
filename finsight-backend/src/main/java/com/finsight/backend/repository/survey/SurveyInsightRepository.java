package com.finsight.backend.repository.survey;

import com.finsight.backend.entity.survey.SurveyInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SurveyInsightRepository extends JpaRepository<SurveyInsight, Long> {
    List<SurveyInsight> findBySurveyId(Long surveyId);
    void deleteBySurveyId(Long surveyId);
}
