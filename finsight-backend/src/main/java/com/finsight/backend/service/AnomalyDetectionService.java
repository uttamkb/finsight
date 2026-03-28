package com.finsight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.dto.AnomalyInsightDto;
import com.finsight.backend.dto.GeminiAnomalyResponse;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.ForensicAnomaly;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.ForensicAnomalyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnomalyDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionService.class);

    @org.springframework.beans.factory.annotation.Value("${ai.gemini.model:models/gemini-2.5-flash}")
    private String geminiModel;

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/";

    private final BankTransactionRepository bankTransactionRepository;
    private final ForensicAnomalyRepository forensicAnomalyRepository;
    private final AppConfigService appConfigService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String ANOMALY_PROMPT = """
        You are an expert forensic financial auditor for an Apartment Association.
        I will provide you with a JSON array of recent bank transactions.
        Your task is to analyze these transactions and identify ONLY highly suspicious anomalies.
        Look for:
        1. Duplicate payments (similar amount, same vendor, very close dates).
        2. Unusually high amounts for common categories (e.g., spending $5000 on 'Stationery').
        3. Highly suspicious or non-standard vendor names for an apartment association.

        Only return genuine anomalies. If none are found, return an empty array.
        Return the exact transactions you flagged and a concise 'reason' explaining why it is suspicious.

        Return strictly ONLY a JSON object matching this schema:
        {
          "anomalies": [
            {
              "description": "Vendor Name/Description",
              "amount": 123.45,
              "date": "YYYY-MM-DD",
              "reason": "Explain why this is an anomaly in 1 short sentence."
            }
          ]
        }
        """;

    public AnomalyDetectionService(BankTransactionRepository bankTransactionRepository, 
                                   ForensicAnomalyRepository forensicAnomalyRepository,
                                   AppConfigService appConfigService,
                                   HttpClient httpClient,
                                   ObjectMapper objectMapper) {
        this.bankTransactionRepository = bankTransactionRepository;
        this.forensicAnomalyRepository = forensicAnomalyRepository;
        this.appConfigService = appConfigService;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<AnomalyInsightDto> detectAnomalies() {
        AppConfig config = appConfigService.getConfig();
        String apiKey = config.getGeminiApiKey();

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("Gemini API Key is not configured.");
        }

        // Fetch last 100 debit transactions
        PageRequest pageRequest = PageRequest.of(0, 100, Sort.by("txDate").descending());
        List<BankTransaction> recentTxns = bankTransactionRepository
                .findByTenantIdOrderByTxDateDesc("local_tenant", pageRequest)
                .getContent()
                .stream()
                .filter(t -> t.getType() == BankTransaction.TransactionType.DEBIT)
                .toList();

        if (recentTxns.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // Simplify data for the LLM context window
            var simplifiedTxns = recentTxns.stream().map(t -> 
                new SimpleTxn(t.getTxDate().toString(), t.getDescription(), t.getAmount())
            ).toList();
            
            String txnsJson = objectMapper.writeValueAsString(simplifiedTxns);

            String requestBody = String.format("""
                {
                  "contents": [{
                    "parts": [
                      {"text": "%s"},
                      {"text": "TRANSACTIONS TO ANALYZE:\\n%s"}
                    ]
                  }],
                  "generationConfig": {
                    "temperature": 0.2,
                    "responseMimeType": "application/json"
                  }
                }
                """, escapeJson(ANOMALY_PROMPT), escapeJson(txnsJson));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_API_BASE_URL + geminiModel + ":generateContent"))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini API Error: Status={}, Body={}", response.statusCode(), response.body());
                throw new RuntimeException("Failed to analyze anomalies via Gemini API.");
            }

            var rootNode = objectMapper.readTree(response.body());
            String jsonTextContent = rootNode
                    .path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            GeminiAnomalyResponse result = objectMapper.readValue(jsonTextContent, GeminiAnomalyResponse.class);
            List<AnomalyInsightDto> anomalies = result != null && result.getAnomalies() != null ? result.getAnomalies() : new ArrayList<>();
            
            // Persist to DB
            for (AnomalyInsightDto dto : anomalies) {
                ForensicAnomaly entity = new ForensicAnomaly();
                entity.setTenantId("local_tenant");
                entity.setDescription(dto.getDescription());
                entity.setReason(dto.getReason());
                entity.setAmount(dto.getAmount());
                entity.setTxDate(dto.getDate());
                forensicAnomalyRepository.save(entity);
            }

            return anomalies;

        } catch (Exception e) {
            log.error("Failed to execute Anomaly Detection.", e);
            throw new RuntimeException("Anomaly detection failed.", e);
        }
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"").replace("\n", "\\n");
    }

    // Helper class for JSON serialization
    private record SimpleTxn(String date, String description, java.math.BigDecimal amount) {}
}
