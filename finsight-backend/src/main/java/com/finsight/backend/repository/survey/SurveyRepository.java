package com.finsight.backend.repository.survey;

import com.finsight.backend.entity.survey.Survey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SurveyRepository extends JpaRepository<Survey, Long> {
    Optional<Survey> findFirstByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, String status);
}
