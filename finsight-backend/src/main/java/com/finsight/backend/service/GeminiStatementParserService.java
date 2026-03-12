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
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class GeminiStatementParserService {

    private static final Logger log = LoggerFactory.getLogger(GeminiStatementParserService.class);
    // Upgraded to gemini-2.0-flash for faster, cheaper, and structured extraction
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private final AppConfigService appConfigService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Strict prompt to enforce JSON output for fallback
    private static final String PARSING_PROMPT = """
        You are a highly accurate financial data extraction assistant.
        I am providing you with a bank statement in PDF format.
        Please extract all transaction line items from this document.
        For each transaction, extract the following:
        - txDate: The date of the transaction (format as YYYY-MM-DD)
        - description: The description or payee of the transaction
        - type: 'DEBIT' if it's a withdrawal/payment, 'CREDIT' if it's a deposit/income
        - amount: The monetary amount (as a positive number, no currency symbols)
        - category: Briefly categorize this transaction (e.g., 'Groceries', 'Utilities', 'Maintenance', 'Salary', 'Transfer', or 'Other Expense')

        Return the data strictly as a JSON object matching this schema:
        {
          "transactions": [
            {
              "txDate": "YYYY-MM-DD",
              "description": "string",
              "type": "DEBIT|CREDIT",
              "amount": 123.45,
              "category": "string"
            }
          ]
        }
        Do not include any other text or markdown formatting in your response.
        """;

    public GeminiStatementParserService(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
        this.httpClient = HttpClient.newBuilder().build();
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

            List<ParsedBankTransactionDto> localResult = extractLocally(tempFile);

            if ("MODE_LOW_COST".equals(ocrMode)) {
                log.info("Low Cost Mode: Using local extraction results ({} txns).", localResult.size());
                return localResult;
            }

            // Mode is HYBRID
            if (localResult.isEmpty()) {
                log.info("Hybrid Mode: Local extraction yielded 0 transactions. Falling back to Gemini...");
                return extractWithGemini(file, config.getGeminiApiKey());
            } else {
                log.info("Hybrid Mode: Local extraction successful ({} txns).", localResult.size());
                return localResult;
            }

        } catch (Exception e) {
            log.error("Error during statement extraction: {}", e.getMessage(), e);
            if ("MODE_HYBRID".equals(ocrMode)) {
                log.info("Hybrid Mode: Local extraction threw error. Falling back to Gemini...");
                return extractWithGemini(file, config.getGeminiApiKey());
            }
            throw e; // Bubble up if Low Cost fails entirely
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    List<ParsedBankTransactionDto> extractLocally(File tempFile) throws Exception {
        String scriptPath = System.getenv("STATEMENT_SCRIPT_PATH") != null ? System.getenv("STATEMENT_SCRIPT_PATH") : "src/main/resources/scripts/statement_processor.py";
        String pythonPath = System.getenv("PYTHON_EXECUTABLE") != null ? System.getenv("PYTHON_EXECUTABLE") : "src/main/resources/scripts/venv/bin/python3";

        // Check if script exists, if not return empty list
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
            output.append(line);
        }

        boolean finished = process.waitFor(90, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Local Statement Parsing completely timed out.");
        }

        if (process.exitValue() != 0) {
            throw new RuntimeException("Local Statement Parsing failed: " + output.toString());
        }

        String jsonOutput = output.toString();
        // The Python script outputs JSON. We parse it.
        try {
            int startIndex = jsonOutput.indexOf("{");
            int endIndex = jsonOutput.lastIndexOf("}");
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                String cleanJson = jsonOutput.substring(startIndex, endIndex + 1);
                GeminiBankStatementResponse response = objectMapper.readValue(cleanJson, GeminiBankStatementResponse.class);
                if (response.getError() != null && !response.getError().isEmpty()) {
                    throw new RuntimeException("Python script error: " + response.getError());
                }
                return response.getTransactions() != null ? response.getTransactions() : new ArrayList<>();
            } else {
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.warn("Failed to parse output from local python script: {}", e.getMessage());
            throw new RuntimeException("JSON parsing error from local script.", e);
        }
    }

    List<ParsedBankTransactionDto> extractWithGemini(MultipartFile file, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your_gemini_api_key_here")) {
            throw new IllegalStateException("Gemini API Key is missing. Cannot perform AI extraction.");
        }

        byte[] pdfBytes = file.getBytes();
        String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);

        String requestBody = String.format("""
            {
              "contents": [{
                "parts": [
                  {"text": "%s"},
                  {
                    "inline_data": {
                      "mime_type": "application/pdf",
                      "data": "%s"
                    }
                  }
                ]
              }],
              "generationConfig": {
                "temperature": 0.1,
                "responseMimeType": "application/json"
              }
            }
            """, escapeJson(PARSING_PROMPT), base64Pdf);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL + apiKey))
                .header("Content-Type", "application/json")
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

        String cleanText = jsonTextContent.replaceAll("```json|```", "").trim();

        GeminiBankStatementResponse result = objectMapper.readValue(cleanText, GeminiBankStatementResponse.class);
        return result.getTransactions() != null ? result.getTransactions() : new ArrayList<>();
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
