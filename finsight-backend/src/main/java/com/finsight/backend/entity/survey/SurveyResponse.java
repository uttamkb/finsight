package com.finsight.backend.entity.survey;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "survey_responses")
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "question")
    private String question;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @Column(name = "hash", unique = true)
    private String hash;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public Long getSurveyId() { return surveyId; }
    public void setSurveyId(Long surveyId) { this.surveyId = surveyId; }
    public java.time.LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
