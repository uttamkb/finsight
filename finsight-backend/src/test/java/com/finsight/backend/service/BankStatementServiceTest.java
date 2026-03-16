package com.finsight.backend.service;

import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Category;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankStatementServiceTest {

    @Mock private GeminiStatementParserService geminiStatementParserService;
    @Mock private CsvStatementParser csvStatementParser;
    @Mock private BankTransactionRepository bankTransactionRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private VendorManager vendorManager;
    @Mock private BankTransactionCategorizationService categorizationService;

    private BankStatementService bankStatementService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bankStatementService = new BankStatementService(
                geminiStatementParserService,
                csvStatementParser,
                bankTransactionRepository,
                categoryRepository,
                vendorManager,
                categorizationService
        );
    }

    @Test
    void testPersistParsedTransactions_CallsCategorizationEngine() throws Exception {
        // Arrange
        ParsedBankTransactionDto dto = new ParsedBankTransactionDto();
        dto.setVendor("Swiggy");
        dto.setDescription("Swiggy order");
        dto.setAmount(BigDecimal.valueOf(500));
        dto.setType("DEBIT");
        dto.setTxDate("2024-03-15");

        Category mockCategory = new Category();
        mockCategory.setName("Food");
        
        when(categorizationService.categorize(anyString(), anyString(), any(), anyString()))
                .thenReturn("Food");
        when(categorizationService.getOrCreateCategoryEntity(eq("Food"), eq("DEBIT")))
                .thenReturn(mockCategory);
        when(csvStatementParser.parseDateRobustly(anyString())).thenReturn(LocalDate.of(2024, 3, 15));
        when(bankTransactionRepository.existsByReferenceNumberAndTenantId(anyString(), anyString()))
                .thenReturn(false);

        // Act
        org.springframework.mock.web.MockMultipartFile mockFile = 
            new org.springframework.mock.web.MockMultipartFile("file", "test.pdf", "application/pdf", "dummy content".getBytes());
        
        when(geminiStatementParserService.parsePdfStatement(any())).thenReturn(List.of(dto));
        
        int count = bankStatementService.processPdfStatement(mockFile);

        // Assert
        assertEquals(1, count);

        // Assert
        ArgumentCaptor<List<BankTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(bankTransactionRepository).saveAll(captor.capture());
        
        List<BankTransaction> savedList = captor.getValue();
        assertEquals(1, savedList.size());
        assertEquals("Food", savedList.get(0).getCategory().getName());
        verify(categorizationService).categorize(eq("Swiggy"), eq("Swiggy order"), isNull(), eq("DEBIT"));
    }
}
