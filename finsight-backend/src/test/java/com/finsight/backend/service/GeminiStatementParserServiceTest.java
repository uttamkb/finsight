package com.finsight.backend.service;

import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.entity.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GeminiStatementParserServiceTest {

    @Mock
    private AppConfigService appConfigService;

    private GeminiStatementParserService parserService;

    private AppConfig mockConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Create an actual instance, passing the mocked AppConfigService
        parserService = spy(new GeminiStatementParserService(appConfigService));
        
        mockConfig = new AppConfig();
        mockConfig.setGeminiApiKey("test-api-key");
        when(appConfigService.getConfig()).thenReturn(mockConfig);
    }

    private ParsedBankTransactionDto createMockTx(String desc, double amount) {
        ParsedBankTransactionDto dto = new ParsedBankTransactionDto();
        dto.setDescription(desc);
        dto.setAmount(BigDecimal.valueOf(amount));
        dto.setTxDate(LocalDate.now());
        dto.setType("DEBIT");
        return dto;
    }

    @Test
    void testParsePdfStatement_HighAccuracyMode() throws Exception {
        // Arrange
        mockConfig.setOcrMode("MODE_HIGH_ACCURACY");
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", "dummy content".getBytes());
        
        List<ParsedBankTransactionDto> mockAiTxns = List.of(createMockTx("AI TX 1", 100.0));
        doReturn(mockAiTxns).when(parserService).extractWithGemini(any(), anyString());

        // Act
        List<ParsedBankTransactionDto> result = parserService.parsePdfStatement(file);

        // Assert
        assertEquals(1, result.size());
        assertEquals("AI TX 1", result.get(0).getDescription());
        
        verify(parserService, times(1)).extractWithGemini(any(), anyString());
        verify(parserService, never()).extractLocally(any());
    }

    @Test
    void testParsePdfStatement_LowCostMode_Success() throws Exception {
        // Arrange
        mockConfig.setOcrMode("MODE_LOW_COST");
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", "dummy content".getBytes());
        
        List<ParsedBankTransactionDto> mockLocalTxns = List.of(createMockTx("Local TX 1", 50.0));
        doReturn(mockLocalTxns).when(parserService).extractLocally(any(File.class));

        // Act
        List<ParsedBankTransactionDto> result = parserService.parsePdfStatement(file);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Local TX 1", result.get(0).getDescription());
        
        verify(parserService, times(1)).extractLocally(any(File.class));
        verify(parserService, never()).extractWithGemini(any(), anyString());
    }

    @Test
    void testParsePdfStatement_LowCostMode_Fails_NoFallback() throws Exception {
        // Arrange
        mockConfig.setOcrMode("MODE_LOW_COST");
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", "dummy content".getBytes());
        
        doThrow(new RuntimeException("Local extraction failed")).when(parserService).extractLocally(any(File.class));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            parserService.parsePdfStatement(file);
        });
        assertEquals("Local extraction failed", exception.getMessage());
        
        verify(parserService, times(1)).extractLocally(any(File.class));
        verify(parserService, never()).extractWithGemini(any(), anyString());
    }

    @Test
    void testParsePdfStatement_HybridMode_LocalSuccess() throws Exception {
        // Arrange
        mockConfig.setOcrMode("MODE_HYBRID"); // or null defaults to Hybrid
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", "dummy content".getBytes());
        
        List<ParsedBankTransactionDto> mockLocalTxns = List.of(createMockTx("Local TX 1", 80.0));
        doReturn(mockLocalTxns).when(parserService).extractLocally(any(File.class));

        // Act
        List<ParsedBankTransactionDto> result = parserService.parsePdfStatement(file);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Local TX 1", result.get(0).getDescription());
        
        verify(parserService, times(1)).extractLocally(any(File.class));
        verify(parserService, never()).extractWithGemini(any(), anyString());
    }

    @Test
    void testParsePdfStatement_HybridMode_LocalReturnsEmpty_FallbackToGemini() throws Exception {
        // Arrange
        mockConfig.setOcrMode("MODE_HYBRID");
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", "dummy content".getBytes());
        
        // Local returns 0 transactions
        doReturn(new ArrayList<>()).when(parserService).extractLocally(any(File.class));
        
        List<ParsedBankTransactionDto> mockAiTxns = List.of(createMockTx("Fallback AI TX", 120.0));
        doReturn(mockAiTxns).when(parserService).extractWithGemini(any(), anyString());

        // Act
        List<ParsedBankTransactionDto> result = parserService.parsePdfStatement(file);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Fallback AI TX", result.get(0).getDescription());
        
        verify(parserService, times(1)).extractLocally(any(File.class));
        verify(parserService, times(1)).extractWithGemini(any(), anyString());
    }

    @Test
    void testParsePdfStatement_HybridMode_LocalThrowsError_FallbackToGemini() throws Exception {
        // Arrange
        mockConfig.setOcrMode("MODE_HYBRID");
        MockMultipartFile file = new MockMultipartFile("file", "statement.pdf", "application/pdf", "dummy content".getBytes());
        
        // Local throws exception
        doThrow(new RuntimeException("Local crashed")).when(parserService).extractLocally(any(File.class));
        
        List<ParsedBankTransactionDto> mockAiTxns = List.of(createMockTx("Fallback AI TX 2", 150.0));
        doReturn(mockAiTxns).when(parserService).extractWithGemini(any(), anyString());

        // Act
        List<ParsedBankTransactionDto> result = parserService.parsePdfStatement(file);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Fallback AI TX 2", result.get(0).getDescription());
        
        verify(parserService, times(1)).extractLocally(any(File.class));
        verify(parserService, times(1)).extractWithGemini(any(), anyString());
    }
}
