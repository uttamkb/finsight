package com.finsight.backend.controller;

import com.finsight.backend.dto.ReconciliationResultDto;
import com.finsight.backend.entity.ReconciliationRun;
import com.finsight.backend.repository.ReconciliationRunRepository;
import com.finsight.backend.service.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReconciliationController.class)
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
class ReconciliationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReconciliationService reconciliationService;

    @MockBean
    private ReconciliationRunRepository reconciliationRunRepository;

    @Test
    void testGetReconciliationRuns_ReturnsList() throws Exception {
        ReconciliationRun run = new ReconciliationRun();
        run.setStatus("COMPLETED");
        run.setTenantId("local_tenant");
        
        when(reconciliationRunRepository.findByTenantIdOrderByStartedAtDesc("local_tenant"))
                .thenReturn(List.of(run));

        mockMvc.perform(get("/api/v1/reconciliation/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void testRunReconciliation_TriggersService() throws Exception {
        ReconciliationResultDto result = new ReconciliationResultDto();
        result.setMatchedCount(5);
        
        when(reconciliationService.runReconciliation(anyString(), anyString()))
                .thenReturn(result);

        mockMvc.perform(post("/api/v1/reconciliation/run")
                .param("accountType", "MAINTENANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(5));
    }
}
