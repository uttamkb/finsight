package com.finsight.backend.service;

import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.entity.Category;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.StatementUploadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import org.mockito.ArgumentCaptor;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.finsight.backend.entity.BankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.finsight.backend.dto.BankTransactionDto;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BankStatementServiceTest {

    @Mock private GeminiStatementParserService geminiStatementParserService;
    @Mock private CsvStatementParser csvStatementParser;
    @Mock private XlsxStatementParser xlsxStatementParser;
    @Mock private BankTransactionRepository bankTransactionRepository;
    @Mock private BankTransactionCategorizationService categorizationService;
    @Mock private TransactionPatternEnricher transactionPatternEnricher;
    @Mock private StatementUploadRepository statementUploadRepository;
    @Mock private AppConfigService appConfigService;
    @Mock private VendorManager vendorManager;

    private BankStatementService bankStatementService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bankStatementService = new BankStatementService(
                geminiStatementParserService,
                csvStatementParser,
                xlsxStatementParser,
                bankTransactionRepository,
                categorizationService,
                transactionPatternEnricher,
                statementUploadRepository,
                appConfigService,
                vendorManager
        );
        bankStatementService.setUploadBaseDir("target/test-uploads");
        
        com.finsight.backend.entity.AppConfig config = new com.finsight.backend.entity.AppConfig();
        config.setOcrMode("MODE_MAX_INTELLIGENCE");
        config.setGeminiApiKey("fake-key");
        when(appConfigService.getConfig()).thenReturn(config);
    }

    @AfterEach
    void tearDown() throws IOException {
        java.nio.file.Path testDir = java.nio.file.Paths.get("target/test-uploads");
        if (java.nio.file.Files.exists(testDir)) {
            java.nio.file.Files.walk(testDir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(java.io.File::delete);
        }
    }

    @Test
    void testProcessPdfStatement() throws Exception {
        // Arrange
        byte[] content = "fake-pdf-content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", content);
        
        java.io.File tempFile = new java.io.File("target/test-uploads/tmp/test-" + System.currentTimeMillis() + ".pdf");
        tempFile.getParentFile().mkdirs(); // Ensure parent directories exist
        if (tempFile.exists()) tempFile.delete();
        tempFile.createNewFile();
        
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tempFile)) {
            fos.write(content);
        }

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
        when(categorizationService.getOrCreateCategoryEntity(eq("Food"), anyString()))
                .thenReturn(mockCategory);
        when(csvStatementParser.parseDateRobustly(anyString())).thenReturn(LocalDate.of(2024, 3, 15));
        when(bankTransactionRepository.existsByReferenceNumberAndTenantId(anyString(), anyString()))
                .thenReturn(false);
        
        when(geminiStatementParserService.parsePdfStatement(any())).thenReturn(List.of(dto));
        
        // Act
        int count = bankStatementService.processPdfStatement(tempFile);

        // Assert
        assertEquals(1, count);
        verify(bankTransactionRepository).saveAll(anyList());
    }

    @Test
    void testGetRecentUploads_TenantIsolation() {
        // Arrange
        String tenantId = "tenant-1";
        when(statementUploadRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(java.util.List.of(new com.finsight.backend.entity.StatementUpload()));

        // Act
        var results = bankStatementService.getRecentUploads(tenantId);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        verify(statementUploadRepository).findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Test
    void testProcessStatementAsync_StartsWithRunningStatus() throws Exception {
        // Arrange
        String tenantId = "tenant-1";
        org.springframework.web.multipart.MultipartFile mockFile = mock(org.springframework.web.multipart.MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("statement.pdf");
        // Return empty stream so it throws early internally without NPE on hash
        when(mockFile.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));

        when(statementUploadRepository.findByFileHashAndTenantIdAndStatus(anyString(), eq(tenantId), eq("COMPLETED")))
                .thenReturn(java.util.Optional.empty());

        // Act — triggers async, status should be set eventually
        bankStatementService.processStatementAsync(tenantId, mockFile, "MAINTENANCE");

        // Status should be initialised (not null)
        var status = bankStatementService.getUploadStatus(tenantId);
        assertNotNull(status, "Upload status should be initialized after async call");
    }

    @Test
    void testGetPagedTransactions_WithAllFilters() {
        // Arrange
        String tenantId = "tenant-1";
        PageRequest pageable = PageRequest.of(0, 10);
        BankTransaction txn = new BankTransaction();
        txn.setId(1L);
        txn.setAmount(new BigDecimal("100.00"));
        txn.setType(BankTransaction.TransactionType.DEBIT);
        
        when(bankTransactionRepository.findByTenantIdAndAccountTypeWithFilters(
                eq(tenantId), any(), eq(BankTransaction.TransactionType.DEBIT), eq(true), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(txn)));

        // Act
        Page<BankTransactionDto> result = bankStatementService.getPagedTransactions(
                tenantId, pageable, true, "DEBIT", "2024-01-01", "2024-01-31", "MAINTENANCE");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(bankTransactionRepository).findByTenantIdAndAccountTypeWithFilters(
                eq(tenantId), any(), eq(BankTransaction.TransactionType.DEBIT), eq(true), 
                eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 1, 31)), eq(pageable));
    }

    @Test
    void testUpdateTransaction_EnrichesVendor() {
        // Arrange
        Long txnId = 1L;
        BankTransaction txn = new BankTransaction();
        txn.setId(txnId);
        txn.setVendor("Old Vendor");
        
        BankTransactionDto dto = new BankTransactionDto();
        dto.setVendor("New Vendor");
        dto.setAmount(new BigDecimal("200.00"));
        dto.setType("DEBIT");
        dto.setTxDate(LocalDate.now());

        when(bankTransactionRepository.findById(txnId)).thenReturn(Optional.of(txn));
        when(bankTransactionRepository.save(any())).thenReturn(txn);

        // Act
        BankTransactionDto result = bankStatementService.updateTransaction(txnId, dto);

        // Assert
        assertNotNull(result);
        assertEquals("New Vendor", txn.getVendor());
        verify(transactionPatternEnricher).enrichIfMatches(txn);
        verify(bankTransactionRepository).save(txn);
    }
    @Test
    void testCleanupOldUploads_DeletesFileAndRecord() {
        // Arrange
        com.finsight.backend.entity.StatementUpload upload = new com.finsight.backend.entity.StatementUpload();
        upload.setFilePath("target/test-uploads/old-file.pdf");
        
        java.io.File oldFile = new java.io.File("target/test-uploads/old-file.pdf");
        oldFile.getParentFile().mkdirs();
        try { oldFile.createNewFile(); } catch (Exception e) {}

        when(statementUploadRepository.findByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(List.of(upload));

        // Act
        bankStatementService.cleanupOldUploads();

        // Assert
        assertFalse(oldFile.exists());
        verify(statementUploadRepository).deleteOlderThan(any());
    }

    @Test
    void testPersistParsedTransactions_Deduplication() throws Exception {
        // Arrange
        ParsedBankTransactionDto dto = new ParsedBankTransactionDto();
        dto.setTxDate("2024-03-15");
        dto.setAmount(new BigDecimal("500"));
        dto.setDescription("Double Entry");
        dto.setType("DEBIT");

        when(geminiStatementParserService.parsePdfStatement(any())).thenReturn(List.of(dto));
        when(csvStatementParser.parseDateRobustly(anyString())).thenReturn(LocalDate.of(2024, 3, 15));
        // First call: exists = true, skip
        when(bankTransactionRepository.existsByReferenceNumberAndTenantId(anyString(), anyString()))
                .thenReturn(true);

        // Act
        int count = bankStatementService.processPdfStatement(createMockTempFile());

        // Assert
        assertEquals(0, count);
        verify(bankTransactionRepository).saveAll(argThat(list -> ((List<?>)list).isEmpty()));
    }

    @Test
    void testPersistParsedTransactions_AiMetadataAndStatus() throws Exception {
        // Arrange
        ParsedBankTransactionDto dto = new ParsedBankTransactionDto();
        dto.setTxDate("2024-03-15");
        dto.setAmount(new BigDecimal("500"));
        dto.setDescription("AI Txn");
        dto.setType("DEBIT");
        dto.setConfidenceScore(0.95);
        dto.setAiReasoning("High confidence match");

        when(geminiStatementParserService.parsePdfStatement(any())).thenReturn(List.of(dto));
        when(csvStatementParser.parseDateRobustly(anyString())).thenReturn(LocalDate.of(2024, 3, 15));
        when(bankTransactionRepository.existsByReferenceNumberAndTenantId(anyString(), anyString()))
                .thenReturn(false);
        
        Category mockCategory = new Category();
        when(categorizationService.categorize(any(), any(), any(), any())).thenReturn("Test");
        when(categorizationService.getOrCreateCategoryEntity(any(), any())).thenReturn(mockCategory);

        // Act
        bankStatementService.processPdfStatement(createMockTempFile());

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BankTransaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(bankTransactionRepository).saveAll(captor.capture());
        BankTransaction saved = captor.getValue().get(0);
        assertEquals("AUTO_VALIDATED", saved.getStatus());
        assertEquals(0.95, saved.getConfidenceScore());
    }

    @Test
    void testProcessPersistentFileAsync_UsesGeminiForPdf() throws Exception {
        // Arrange
        com.finsight.backend.entity.StatementUpload upload = new com.finsight.backend.entity.StatementUpload();
        upload.setFileId("file-1");
        upload.setFileName("test.pdf");
        upload.setFilePath(createMockTempFile().getAbsolutePath());
        upload.setTenantId("tenant-1");
        upload.setAccountType("MAINTENANCE");

        ParsedBankTransactionDto dto = new ParsedBankTransactionDto();
        dto.setAmount(new BigDecimal("100"));
        dto.setTxDate("2024-03-15");
        dto.setType("DEBIT");
        
        when(geminiStatementParserService.parsePdfStatement(any())).thenReturn(List.of(dto));
        when(csvStatementParser.parseDateRobustly(anyString())).thenReturn(LocalDate.of(2024, 3, 15));
        when(categorizationService.categorize(any(), any(), any(), any())).thenReturn("Misc");
        when(categorizationService.getOrCreateCategoryEntity(any(), any())).thenReturn(new Category());

        // Act
        bankStatementService.processPersistentFileAsync(upload);

        // Assert
        verify(geminiStatementParserService, timeout(2000)).parsePdfStatement(any());
        verify(bankTransactionRepository, timeout(2000)).saveAll(anyList());
        verify(statementUploadRepository, atLeastOnce()).save(argThat(u -> "COMPLETED".equals(u.getStatus())));
    }

    private java.io.File createMockTempFile() throws IOException {
        java.io.File dir = new java.io.File("target/test-uploads");
        if (!dir.exists()) dir.mkdirs();
        java.io.File temp = java.io.File.createTempFile("mock", ".pdf", dir);
        java.nio.file.Files.write(temp.toPath(), "mock content".getBytes());
        return temp;
    }
}
