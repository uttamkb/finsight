package com.finsight.backend.controller;

import com.finsight.backend.entity.Feedback;
import com.finsight.backend.repository.FeedbackRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feedback")
@CrossOrigin(origins = "*")
@Tag(name = "User Feedback", description = "Endpoints for submitting and managing user feedback and bug reports")
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;

    public FeedbackController(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit Feedback", description = "Allows users to submit bug reports, feature requests, or general suggestions.")
    public ResponseEntity<Feedback> submitFeedback(@RequestBody Feedback feedback) {
        if (feedback.getTenantId() == null) {
            feedback.setTenantId("local_tenant");
        }
        return ResponseEntity.ok(feedbackRepository.save(feedback));
    }

    @GetMapping("/list")
    @Operation(summary = "List Feedback", description = "Retrieves all feedback for a specific tenant.")
    public ResponseEntity<List<Feedback>> listFeedback(@RequestParam(defaultValue = "local_tenant") String tenantId) {
        return ResponseEntity.ok(feedbackRepository.findByTenantIdOrderBySubmittedAtDesc(tenantId));
    }
}
