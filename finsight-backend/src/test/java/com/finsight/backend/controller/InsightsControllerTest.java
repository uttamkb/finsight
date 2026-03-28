package com.finsight.backend.controller;

import com.finsight.backend.dto.CategoryInsightDto;
import com.finsight.backend.dto.VendorInsightDto;
import com.finsight.backend.repository.ForensicAnomalyRepository;
import com.finsight.backend.service.AnomalyDetectionService;
import com.finsight.backend.service.VendorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InsightsController.class)
class InsightsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VendorService vendorService;

    @MockitoBean
    private AnomalyDetectionService anomalyDetectionService;

    @MockitoBean
    private ForensicAnomalyRepository forensicAnomalyRepository;

    private static final String API_KEY = "dev_secret_only_for_local";

    @Test
    void getTopVendors_ReturnsList() throws Exception {
        VendorInsightDto dto = new VendorInsightDto("Swiggy", BigDecimal.valueOf(100), 1L);
        when(vendorService.getTopVendors(anyInt())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/insights/vendors/top")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vendorName").value("Swiggy"));
    }

    @Test
    void getCategorySpending_ReturnsList() throws Exception {
        CategoryInsightDto dto = new CategoryInsightDto("Food", BigDecimal.valueOf(500));
        when(vendorService.getSpendingByCategory(anyString())).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/insights/categories/spend")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryName").value("Food"));
    }

    @Test
    void getOcrStats_ReturnsMap() throws Exception {
        when(vendorService.getOcrModeStats()).thenReturn(Map.of("GEMINI", 10L));

        mockMvc.perform(get("/api/v1/insights/ocr-stats")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.GEMINI").value(10));
    }

    @Test
    void getAnomalyHistory_ReturnsList() throws Exception {
        when(forensicAnomalyRepository.findByTenantIdOrderByTxDateDesc(anyString()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/insights/anomalies/history")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getCategoryDrilldown_ReturnsMap() throws Exception {
        when(vendorService.getCategoryDrilldown(anyString(), anyString()))
                .thenReturn(Map.of("categoryName", "Food"));

        mockMvc.perform(get("/api/v1/insights/categories/Food/drilldown")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Food"));
    }
}
