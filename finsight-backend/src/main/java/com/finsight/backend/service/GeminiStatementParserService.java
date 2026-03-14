package com.finsight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finsight.backend.dto.GeminiBankStatementResponse;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.entity.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import java.io.InputStreamReader;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

@Service
public class GeminiStatementParserService {

    private static final Logger log = LoggerFactory.getLogger(GeminiStatementParserService.class);
    // Upgraded to gemini-3-flash-preview for faster, cheaper, and structured extraction
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent";

    private final AppConfigService appConfigService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Improved prompt with Counterparty Intelligence
    private static final String PARSING_PROMPT = """
        You are a highly accurate financial data extraction assistant.
        I am providing you with a chunk of a bank statement PDF.
        Please extract all transaction line items from this document.

        EXTRACTION RULES:
        1. Each table row represents a transaction.
        2. If Withdrawal (Dr) has a value -> type = "DEBIT".
        3. If Deposit (Cr) has a value -> type = "CREDIT".
        4. Remove commas and currency symbols from numeric values.
        5. Convert dates to ISO format: YYYY-MM-DD.
        6. Ignore headers, totals, and non-transaction rows.

        COUNTERPARTY EXTRACTION RULES:
        1. The "Remarks" or "Description" column contains the counterparty/narration.
        2. If type = CREDIT:
           - counterparty = the sender or payer name.
           - Example: "UPI/JohnDoe/HDFC BANK" -> counterparty = "JohnDoe"
        3. If type = DEBIT:
           - counterparty = merchant or vendor name.
           - Example: "UPI/Swiggy/HDFC BANK" -> counterparty = "Swiggy"
        4. Remove bank names and transaction channel prefixes such as: UPI, IMPS, NEFT, POS, CARD, HDFC BANK, ICICI BANK, etc.
        5. The vendor/counterparty should be a clean human or merchant name.

        Return the data strictly as a JSON object matching this schema:
        {
          "transactions": [
            {
              "txDate": "YYYY-MM-DD",
              "description": "Original narration",
              "vendor": "Cleaned merchant/person name",
              "type": "DEBIT|CREDIT",
              "amount": number,
              "category": "string"
            }
          ]
        }
        Do not include any other text or markdown formatting.
        """;

    private static final int PAGES_PER_CHUNK = 2;

    public GeminiStatementParserService(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public List<ParsedBankTransactionDto> parsePdfStatement(MultipartFile file) throws Exception {
        AppConfig config = appConfigService.getConfig();
        String ocrMode = config.getOcrMode() != null ? config.getOcrMode() : "MODE_HYBRID";
        log.info("Starting PDF Statement Parse. File: {}, Mode: {}", file.getOriginalFilename(), ocrMode);

        File tempFile = null;
        try {
            if ("MODE_HIGH_ACCURACY".equals(ocrMode)) {
                return extractWithGemini(file, config.getGeminiApiKey());
            }

            // For Low Cost or Hybrid, try local extraction first
            File tempDir = new File(System.getProperty("user.dir"), "target");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            if (tempDir.exists() && tempDir.canWrite()) {
                tempFile = File.createTempFile("stmt_", ".pdf", tempDir);
            } else {
                tempFile = File.createTempFile("stmt_", ".pdf");
            }

            file.transferTo(tempFile);

            GeminiBankStatementResponse localResponse = extractLocally(tempFile);
            List<ParsedBankTransactionDto> localResult = localResponse.getTransactions() != null ? localResponse.getTransactions() : new ArrayList<>();
            double confidence = localResponse.getConfidenceScore();

            log.info("Local extraction confidence: {}%", confidence);

            if ("MODE_LOW_COST".equals(ocrMode)) {
                log.info("Low Cost Mode: Using local results regardless of confidence.");
                return localResult;
            }

            // Mode is HYBRID
            if (localResult.isEmpty() || confidence < 70) {
                log.info("Confidence ({}%) < 70% or 0 transactions. Falling back to Gemini AI Parser...", confidence);
                return extractWithGemini(file, config.getGeminiApiKey());
            } else {
                log.info("Hybrid Mode: Local extraction successful with {}% confidence ({} txns).", confidence, localResult.size());
                return localResult;
            }

        } catch (Exception e) {
            log.error("Error during statement extraction: {}", e.getMessage(), e);
            if ("MODE_HYBRID".equals(ocrMode)) {
                log.info("Hybrid Mode: Local extraction failed. Falling back to Gemini...");
                return extractWithGemini(file, config.getGeminiApiKey());
            }
            throw e;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    GeminiBankStatementResponse extractLocally(File tempFile) throws Exception {
        String scriptPath = System.getenv("STATEMENT_SCRIPT_PATH") != null ? System.getenv("STATEMENT_SCRIPT_PATH") : "src/main/resources/scripts/statement_processor.py";
        String pythonPath = System.getenv("PYTHON_EXECUTABLE") != null ? System.getenv("PYTHON_EXECUTABLE") : "src/main/resources/scripts/venv/bin/python3";

        if (!new File(scriptPath).exists()) {
            throw new RuntimeException("Local parsing script missing: " + scriptPath);
        }

        ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath, tempFile.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Local Statement Parsing completely timed out.");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Local Statement Parsing failed: " + output.toString());
        }

        String jsonOutput = output.toString();
        try {
            int startIndex = jsonOutput.indexOf("{");
            int endIndex = jsonOutput.lastIndexOf("}");
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                String cleanJson = jsonOutput.substring(startIndex, endIndex + 1);
                GeminiBankStatementResponse response = objectMapper.readValue(cleanJson, GeminiBankStatementResponse.class);
                if (response.getError() != null && !response.getError().isEmpty()) {
                    throw new RuntimeException("Python script error: " + response.getError());
                }
                if (response.getDebug() != null) {
                    response.getDebug().forEach(msg -> log.info("[Python Debug] {}", msg));
                }
                return response;
            } else {
                return new GeminiBankStatementResponse();
            }
        } catch (Exception e) {
            log.warn("Failed to parse output from local python script: {}", e.getMessage(), e);
            throw new RuntimeException("JSON parsing error from local script.", e);
        }
    }

    List<ParsedBankTransactionDto> extractWithGemini(MultipartFile file, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your_gemini_api_key_here")) {
            throw new IllegalStateException("Gemini API Key is missing. Cannot perform AI extraction.");
        }

        List<ParsedBankTransactionDto> allTransactions = new ArrayList<>();
        
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(file.getBytes())) {
            int pageCount = document.getNumberOfPages();
            log.info("PDF has {} pages. Processing in chunks of {} pages.", pageCount, PAGES_PER_CHUNK);

            for (int i = 0; i < pageCount; i += PAGES_PER_CHUNK) {
                int end = Math.min(i + PAGES_PER_CHUNK, pageCount);
                log.info("Processing chunk: pages {} to {}", i + 1, end);
                
                try (org.apache.pdfbox.pdmodel.PDDocument chunk = new org.apache.pdfbox.pdmodel.PDDocument()) {
                    for (int j = i; j < end; j++) {
                        chunk.addPage(document.getPage(j));
                    }
                    
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    chunk.save(baos);
                    byte[] chunkBytes = baos.toByteArray();
                    
                    List<ParsedBankTransactionDto> chunkResult = callGeminiForChunk(chunkBytes, apiKey);
                    if (chunkResult != null) {
                        allTransactions.addAll(chunkResult);
                    }
                }
            }
        }

        log.info("Successfully extracted {} transactions across all chunks.", allTransactions.size());
        return allTransactions;
    }

    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 5000, multiplier = 2))
    protected List<ParsedBankTransactionDto> callGeminiForChunk(byte[] pdfBytes, String apiKey) throws Exception {
        // Step 1: Upload via Gemini File API
        String fileUri = uploadChunkToGemini(pdfBytes, apiKey);

        // Step 2: Use fileUri in Generation Request
        String requestBody = String.format("""
            {
              "contents": [{
                "parts": [
                  {"text": "%s"},
                  {
                    "file_data": {
                      "mime_type": "application/pdf",
                      "file_uri": "%s"
                    }
                  }
                ]
              }],
              "generationConfig": {
                "temperature": 0.1,
                "responseMimeType": "application/json"
              }
            }
            """, escapeJson(PARSING_PROMPT), fileUri);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL))
                .header("Content-Type", "application/json")
                .header("Connection", "close")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Gemini API Error: Status={}, Body={}", response.statusCode(), response.body());
            throw new RuntimeException("Failed to extract data via Gemini API. HTTP " + response.statusCode());
        }

        var rootNode = objectMapper.readTree(response.body());
        String jsonTextContent = rootNode
                .path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        String cleanText = jsonTextContent.trim();
        int firstBrace = cleanText.indexOf('{');
        int lastBrace = cleanText.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleanText = cleanText.substring(firstBrace, lastBrace + 1);
        }

        GeminiBankStatementResponse result = objectMapper.readValue(cleanText, GeminiBankStatementResponse.class);
        return result.getTransactions() != null ? result.getTransactions() : new ArrayList<>();
    }

    private String uploadChunkToGemini(byte[] pdfBytes, String apiKey) throws Exception {
        String uploadUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + apiKey;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("X-Goog-Upload-Protocol", "raw")
                .header("X-Goog-Upload-Command", "start, upload, finalize")
                .header("X-Goog-Upload-Header-Content-Length", String.valueOf(pdfBytes.length))
                .header("X-Goog-Upload-Header-Content-Type", "application/pdf")
                .header("Content-Type", "application/pdf")
                .header("Connection", "close")
                .timeout(Duration.ofMinutes(3))
                .POST(HttpRequest.BodyPublishers.ofByteArray(pdfBytes))
                .build();
                
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            log.error("Gemini Upload API Error: Status={}, Body={}", response.statusCode(), response.body());
            throw new RuntimeException("Failed to upload chunk via Gemini API. HTTP " + response.statusCode());
        }
        
        var uploadNode = objectMapper.readTree(response.body());
        return uploadNode.path("file").path("uri").asText();
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
