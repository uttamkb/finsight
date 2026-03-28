package com.finsight.backend.repository.survey;

import com.finsight.backend.entity.survey.SurveyActionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SurveyActionItemRepository extends JpaRepository<SurveyActionItem, Long> {
    List<SurveyActionItem> findBySurveyId(Long surveyId);
    Optional<SurveyActionItem> findBySurveyIdAndFacilityAndAction(Long surveyId, String facility, String action);
    void deleteBySurveyIdAndStatus(Long surveyId, String status);
    
    boolean existsByHash(String hash);
    Optional<SurveyActionItem> findByHash(String hash);
}
