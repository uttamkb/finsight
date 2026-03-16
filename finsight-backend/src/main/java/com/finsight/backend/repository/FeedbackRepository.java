package com.finsight.backend.repository;

import com.finsight.backend.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByTenantIdOrderBySubmittedAtDesc(String tenantId);
}
