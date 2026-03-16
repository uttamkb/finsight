package com.finsight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.dto.GeminiBankStatementResponse;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Dedicated client for interacting with the Gemini API for bank statement parsing.
 * Implements retries and generous timeouts to handle transient network issues.
 */
@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiClient(ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a plain text chunk to Gemini with retries.
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 5000, multiplier = 2))
    public List<ParsedBankTransactionDto> callGeminiWithText(String textChunk, String prompt, String apiKey) throws Exception {
        log.info("Sending text chunk to Gemini (Size: {}). Retry attempted if failed.", textChunk.length());
        
        String requestBody = String.format("""
            {
              "contents": [{
                "parts": [
                  {"text": "%s"},
                  {"text": "STATEMENT TEXT:\\n%s"}
                ]
              }],
              "generationConfig": {"temperature": 0.1, "responseMimeType": "application/json"}
            }
            """, escapeJson(prompt), escapeJson(textChunk));

        return sendToGeminiAndParse(requestBody, apiKey);
    }

    /**
     * Sends a rendered page PNG image to Gemini with retries.
     */
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 10000, multiplier = 2))
    public List<ParsedBankTransactionDto> callGeminiWithImage(byte[] imageBytes, String prompt, String apiKey) throws Exception {
        log.info("Sending image page to Gemini (Size: {} KB). Retry attempted if failed.", imageBytes.length / 1024);
        
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        String requestBody = String.format("""
            {
              "contents": [{
                "parts": [
                  {"text": "%s"},
                  {
                    "inline_data": {
                      "mime_type": "image/png",
                      "data": "%s"
                    }
                  }
                ]
              }],
              "generationConfig": {"temperature": 0.1, "responseMimeType": "application/json"}
            }
            """, escapeJson(prompt), base64Image);

        return sendToGeminiAndParse(requestBody, apiKey);
    }

    private List<ParsedBankTransactionDto> sendToGeminiAndParse(String requestBody, String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofMinutes(5)) // Increased from 3m to 5m
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Gemini API Error: Status={}, Body={}", response.statusCode(), response.body());
            throw new RuntimeException("Gemini extraction failed. HTTP " + response.statusCode());
        }

        var rootNode = objectMapper.readTree(response.body());
        String jsonTextContent = rootNode
                .path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        // Strip markdown fences if present
        String cleanText = jsonTextContent.trim()
                .replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();

        int firstBrace = cleanText.indexOf('{');
        int lastBrace = cleanText.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            cleanText = cleanText.substring(firstBrace, lastBrace + 1);
        }

        GeminiBankStatementResponse result = objectMapper.readValue(cleanText, GeminiBankStatementResponse.class);
        return result.getTransactions() != null ? result.getTransactions() : new ArrayList<>();
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
