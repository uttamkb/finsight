package com.finsight.backend.entity.survey;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "survey_action_items")
public class SurveyActionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "priority")
    private String priority; // HIGH, MEDIUM, LOW

    @Column(name = "facility")
    private String facility;

    @Column(name = "action", length = 1000)
    private String action;

    @Column(name = "timeline")
    private String timeline;

    @Column(name = "expected_outcome", length = 1000)
    private String expectedOutcome;

    @Column(name = "status")
    private String status; // TODO, IN_PROGRESS, DONE, NOT_FEASIBLE

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "hash", unique = true)
    private String hash;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
        if (status == null) status = "TODO";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSurveyId() { return surveyId; }
    public void setSurveyId(Long surveyId) { this.surveyId = surveyId; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getFacility() { return facility; }
    public void setFacility(String facility) { this.facility = facility; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTimeline() { return timeline; }
    public void setTimeline(String timeline) { this.timeline = timeline; }
    public String getExpectedOutcome() { return expectedOutcome; }
    public void setExpectedOutcome(String expectedOutcome) { this.expectedOutcome = expectedOutcome; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
