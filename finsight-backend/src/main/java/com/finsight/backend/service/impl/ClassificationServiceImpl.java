package com.finsight.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.Category;
import com.finsight.backend.repository.CategoryRepository;
import com.finsight.backend.service.AppConfigService;
import com.finsight.backend.service.ClassificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClassificationServiceImpl implements ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationServiceImpl.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private final AppConfigService appConfigService;
    private final CategoryRepository categoryRepository;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClassificationServiceImpl(AppConfigService appConfigService, CategoryRepository categoryRepository) {
        this.appConfigService = appConfigService;
        this.categoryRepository = categoryRepository;
        this.httpClient = HttpClient.newBuilder().build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String classify(String description, String vendor) {
        AppConfig config = appConfigService.getConfig();
        String apiKey = config.getGeminiApiKey();

        List<Category> categories = categoryRepository.findAll();
        String categoryList = categories.stream()
                .map(Category::getName)
                .collect(Collectors.joining(", "));

        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                return classifyWithGemini(description, vendor, categoryList, apiKey);
            } catch (Exception e) {
                log.warn("Gemini classification failed, falling back to local logic: {}", e.getMessage());
            }
        }

        return classifyLocally(description, vendor, categoryList);
    }

    private String classifyWithGemini(String description, String vendor, String categoryList, String apiKey)
            throws Exception {
        String prompt = String.format("""
                You are a financial clerk for an Apartment Association.
                Categorize this receipt into ONE of these categories: [%s].

                Vendor: %s
                Content: %s

                Return ONLY the category name. If none fit perfectly, pick the closest one.
                """, categoryList, vendor, description);

        String requestBody = String.format("""
                {
                  "contents": [{
                    "parts": [{"text": "%s"}]
                  }],
                  "generationConfig": {
                    "temperature": 0.1,
                    "topK": 1,
                    "topP": 1
                  }
                }
                """, escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            String result = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText().trim();
            return result;
        } else {
            throw new RuntimeException("Gemini API returned error: " + response.statusCode());
        }
    }

    private String classifyLocally(String description, String vendor, String categoryList) {
        String combined = (vendor + " " + description).toLowerCase();

        if (combined.contains("electricity") || combined.contains("eb ") || combined.contains("power"))
            return "Utilities";
        if (combined.contains("water") || combined.contains("tanker"))
            return "Water Supply";
        if (combined.contains("plumber") || combined.contains("electrician") || combined.contains("repair"))
            return "Maintenance";
        if (combined.contains("security") || combined.contains("guard") || combined.contains("protection"))
            return "Security";
        if (combined.contains("lift") || combined.contains("elevator"))
            return "Lift Maintenance";
        if (combined.contains("cleaning") || combined.contains("housekeep") || combined.contains("broom"))
            return "Housekeeping";

        // Default to a fallback category if available in the list
        if (categoryList.contains("Miscellaneous"))
            return "Miscellaneous";
        if (categoryList.contains("Others"))
            return "Others";

        return "Uncategorized";
    }

    private String escapeJson(String text) {
        return text.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
