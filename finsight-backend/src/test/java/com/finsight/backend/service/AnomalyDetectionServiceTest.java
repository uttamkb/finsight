package com.finsight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.ForensicAnomaly;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ForensicAnomalyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnomalyDetectionServiceTest {

    @Mock private BankTransactionRepository bankTransactionRepository;
    @Mock private ForensicAnomalyRepository forensicAnomalyRepository;
    @Mock private AppConfigService appConfigService;
    @Mock private HttpClient httpClient;
    @Mock private HttpResponse<String> httpResponse;

    private AnomalyDetectionService anomalyDetectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        anomalyDetectionService = new AnomalyDetectionService(
                bankTransactionRepository,
                forensicAnomalyRepository,
                appConfigService,
                httpClient,
                objectMapper
        );
    }

    @Test
    void detectAnomalies_Success_PersistsAndReturns() throws Exception {
        // Arrange
        AppConfig config = new AppConfig();
        config.setGeminiApiKey("fake-key");
        when(appConfigService.getConfig()).thenReturn(config);

        BankTransaction txn = new BankTransaction();
        txn.setType(BankTransaction.TransactionType.DEBIT);
        txn.setAmount(new BigDecimal("5000"));
        txn.setTxDate(LocalDate.of(2024, 3, 15));
        txn.setDescription("Stationery");
        
        when(bankTransactionRepository.findByTenantIdOrderByTxDateDesc(anyString(), any()))
                .thenReturn(new PageImpl<>(List.of(txn)));

        String geminiResponse = """
            {
              "candidates": [{
                "content": {
                  "parts": [{
                    "text": "{\\"anomalies\\": [{\\"description\\": \\"Stationery\\", \\"amount\\": 5000, \\"date\\": \\"2024-03-15\\", \\"reason\\": \\"Unusually high for stationery\\"}]}"
                  }]
                }
              }]
            }
            """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(geminiResponse);
        when(httpClient.<String>send(any(), any())).thenReturn(httpResponse);

        // Act
        var results = anomalyDetectionService.detectAnomalies();

        // Assert
        assertEquals(1, results.size());
        assertEquals("Unusually high for stationery", results.get(0).getReason());
        
        ArgumentCaptor<ForensicAnomaly> captor = ArgumentCaptor.forClass(ForensicAnomaly.class);
        verify(forensicAnomalyRepository).save(captor.capture());
        assertEquals(new BigDecimal("5000"), captor.getValue().getAmount());
    }

    @Test
    void detectAnomalies_NoTransactions_ReturnsEmpty() {
        // Arrange
        AppConfig config = new AppConfig();
        config.setGeminiApiKey("fake-key");
        when(appConfigService.getConfig()).thenReturn(config);

        when(bankTransactionRepository.findByTenantIdOrderByTxDateDesc(anyString(), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act
        var results = anomalyDetectionService.detectAnomalies();

        // Assert
        assertTrue(results.isEmpty());
        verifyNoInteractions(httpClient);
    }

    @Test
    void detectAnomalies_MissingApiKey_ThrowsException() {
        // Arrange
        when(appConfigService.getConfig()).thenReturn(new AppConfig());

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> anomalyDetectionService.detectAnomalies());
    }
}
