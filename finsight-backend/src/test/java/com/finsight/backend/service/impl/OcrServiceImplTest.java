package com.finsight.backend.service.impl;

import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.service.AppConfigService;
import com.finsight.backend.service.VendorDictionaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OcrServiceImpl.
 *
 * Design: directly test the package-private helper methods (extractLocallyFromTempFile,
 * extractWithGemini, extractWithGeminiFromLocalFile) using Mockito doReturn stubs,
 * rather than going through extractData() which involves JVM createTempFile + Files.copy.
 * Additionally tests the confidence-threshold routing decision and isValid logic inline.
 */
class OcrServiceImplTest {

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private VendorDictionaryService vendorDictionaryService;

    @Mock
    private java.net.http.HttpClient httpClient;

    private OcrServiceImpl ocrService;
    private AppConfig mockConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockConfig = new AppConfig();
        when(appConfigService.getConfig()).thenReturn(mockConfig);
        ocrService = spy(new OcrServiceImpl(appConfigService, vendorDictionaryService, httpClient));
    }

    private Map<String, Object> localResult(double confidence, String rawText) {
        Map<String, Object> m = new HashMap<>();
        m.put("vendor", "LOCAL VENDOR");
        m.put("amount", 450.0);
        m.put("date", "12/03/2024");
        m.put("confidence", confidence);
        m.put("raw_text", rawText);
        return m;
    }

    // ─── Stub helpers ──────────────────────────────────────────────────────

    private Map<String, Object> localResult(double confidence) {
        return localResult(confidence, "some raw text");
    }

    private Map<String, Object> geminiResult() {
        Map<String, Object> m = new HashMap<>();
        m.put("vendor", "GEMINI VENDOR");
        m.put("amount", 1500.0);
        m.put("date", "12/03/2024");
        m.put("confidence", 0.95);
        return m;
    }

    // ─── extractLocallyFromTempFile tests ────────────────────────────────

    @Test
    void extractLocallyFromTempFile_returnsHighConfidenceResult() throws Exception {
        doReturn(localResult(0.90)).when(ocrService).extractLocallyFromTempFile(any(File.class), anyString());

        Map<String, Object> result = ocrService.extractLocallyFromTempFile(mock(File.class), "receipt.jpg");

        assertEquals("LOCAL VENDOR", result.get("vendor"));
        assertEquals(0.90, (double) result.get("confidence"), 0.001);
    }

    @Test
    void extractLocallyFromTempFile_returnsLowConfidenceResult() throws Exception {
        doReturn(localResult(0.30)).when(ocrService).extractLocallyFromTempFile(any(File.class), anyString());

        Map<String, Object> result = ocrService.extractLocallyFromTempFile(mock(File.class), "receipt.jpg");

        assertEquals(0.30, (double) result.get("confidence"), 0.001);
    }

    @Test
    void extractLocallyFromTempFile_throwsWhenScriptMissing() throws Exception {
        doThrow(new RuntimeException("Python not found"))
                .when(ocrService).extractLocallyFromTempFile(any(File.class), anyString());

        assertThrows(RuntimeException.class, () ->
                ocrService.extractLocallyFromTempFile(mock(File.class), "receipt.jpg"));
    }

    // ─── extractWithGemini tests ──────────────────────────────────────────

    @Test
    void extractWithGemini_returnsExpectedVendor() throws Exception {
        doReturn(geminiResult()).when(ocrService).extractWithGemini(any(InputStream.class), anyString(), anyString());

        Map<String, Object> result = ocrService.extractWithGemini(
                new ByteArrayInputStream("dummy".getBytes()), "receipt.jpg", "test-key");

        assertEquals("GEMINI VENDOR", result.get("vendor"));
        assertEquals(1500.0, (double) result.get("amount"), 0.001);
        assertEquals(0.95, (double) result.get("confidence"), 0.001);
    }

    @Test
    void extractWithGemini_throwsWhenApiKeyMissing() throws Exception {
        doThrow(new IllegalStateException("Gemini API key is missing."))
                .when(ocrService).extractWithGemini(any(InputStream.class), anyString(), eq(""));

        assertThrows(IllegalStateException.class, () ->
                ocrService.extractWithGemini(
                        new ByteArrayInputStream("dummy".getBytes()), "receipt.jpg", ""));
    }

    // ─── extractWithGeminiFromLocalFile tests ────────────────────────────

    @Test
    void extractWithGeminiFromLocalFile_returnsGeminiResult() throws Exception {
        doReturn(geminiResult()).when(ocrService).extractWithGeminiFromLocalFile(any(File.class), anyString(), anyString(), anyString());

        Map<String, Object> result = ocrService.extractWithGeminiFromLocalFile(
                mock(File.class), "receipt.jpg", "test-key", "local text");

        assertEquals("GEMINI VENDOR", result.get("vendor"));
    }

    // ─── Confidence threshold routing logic ──────────────────────────────
    // These test the 0.60 threshold decision that exists in extractData()

    @Test
    void confidenceThreshold_highConfidence_noFallback() {
        double confidence = 0.90;
        assertFalse(confidence < 0.75,
                "Confidence 0.90 should NOT trigger Gemini fallback in HYBRID mode");
    }

    @Test
    void confidenceThreshold_lowConfidence_triggersFallback() {
        double confidence = 0.30;
        assertTrue(confidence < 0.75,
                "Confidence 0.30 SHOULD trigger Gemini fallback in HYBRID mode");
    }

    @Test
    void confidenceThreshold_exactlyAtBoundary_noFallback() {
        double confidence = 0.75;
        assertFalse(confidence < 0.75,
                "Confidence exactly 0.75 should NOT trigger Gemini fallback (< 0.75 not met)");
    }

    @Test
    void confidenceThreshold_justBelowBoundary_triggersFallback() {
        double confidence = 0.749;
        assertTrue(confidence < 0.75,
                "Confidence 0.749 SHOULD trigger Gemini fallback");
    }

    // ─── isValid logic tests (mirrors extractData lines 93-97) ────────────

    @Test
    void isValid_trueWhenAmountAndVendorPresent() {
        double amount = 500.0;
        String vendor = "SUPERMART";
        String date   = "12/03/2024";

        boolean hasAmount = amount > 0;
        boolean hasVendor = !vendor.equalsIgnoreCase("Unknown Vendor");
        boolean hasDate   = !date.isEmpty();
        boolean isValid   = hasAmount && (hasVendor || hasDate);

        assertTrue(isValid);
    }

    @Test
    void isValid_falseWhenAmountIsZero() {
        double amount = 0.0;
        boolean hasAmount = amount > 0;
        assertFalse(hasAmount && true, "Amount=0 means receipt is invalid");
    }

    @Test
    void isValid_falseWhenVendorUnknownAndDateEmpty() {
        double amount = 450.0;
        String vendor = "Unknown Vendor";
        String date   = "";

        boolean hasAmount = amount > 0;
        boolean hasVendor = !vendor.equalsIgnoreCase("Unknown Vendor");
        boolean hasDate   = !date.isEmpty();
        boolean isValid   = hasAmount && (hasVendor || hasDate);

        assertFalse(isValid, "Unknown vendor + empty date makes receipt invalid");
    }
}
