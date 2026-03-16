package com.finsight.backend.controller;

import com.finsight.backend.entity.Feedback;
import com.finsight.backend.repository.FeedbackRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class FeedbackControllerTest {

    private final FeedbackRepository feedbackRepository = Mockito.mock(FeedbackRepository.class);
    private final FeedbackController controller = new FeedbackController(feedbackRepository);

    @Test
    void testSubmitFeedback_SavesAndReturnsFeedback() {
        Feedback feedback = new Feedback();
        feedback.setMessage("Test message");
        feedback.setType(Feedback.FeedbackType.BUG);

        when(feedbackRepository.save(any(Feedback.class))).thenReturn(feedback);

        ResponseEntity<Feedback> response = controller.submitFeedback(feedback);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Test message", response.getBody().getMessage());
        assertEquals("local_tenant", response.getBody().getTenantId());
    }

    @Test
    void testListFeedback_ReturnsFeedbackList() {
        Feedback f = new Feedback();
        f.setTenantId("local_tenant");
        
        when(feedbackRepository.findByTenantIdOrderBySubmittedAtDesc("local_tenant"))
                .thenReturn(List.of(f));

        ResponseEntity<List<Feedback>> response = controller.listFeedback("local_tenant");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }
}
