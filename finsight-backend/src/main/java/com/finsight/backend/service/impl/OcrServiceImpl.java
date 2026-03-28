package com.finsight.backend.service.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.service.AppConfigService;
import com.finsight.backend.service.OcrService;
import com.finsight.backend.service.VendorDictionaryService;

@Service
public class OcrServiceImpl implements OcrService {
    private static final Logger log = LoggerFactory.getLogger(OcrServiceImpl.class);

    private final AppConfigService appConfigService;
    private final VendorDictionaryService vendorDictionaryService;
    private final HttpClient httpClient;

    @org.springframework.beans.factory.annotation.Value("${ai.gemini.model:models/gemini-2.5-flash}")
    private String geminiModel;

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OcrServiceImpl(AppConfigService appConfigService,
                          VendorDictionaryService vendorDictionaryService,
                          HttpClient httpClient) {
        this.appConfigService = appConfigService;
        this.vendorDictionaryService = vendorDictionaryService;
        this.httpClient = httpClient;
    }

    @Override
    public Map<String, Object> extractData(InputStream content, String fileName, String mode) {
        Map<String, Object> result = new HashMap<>();
        AppConfig config = appConfigService.getConfig();
        File tempFile = null;

        try {
            if ("MODE_HIGH_ACCURACY".equals(mode)) {
                return extractWithGemini(content, fileName, config.getGeminiApiKey());
            }

            tempFile = File.createTempFile("ocr_", "_" + fileName);
            Files.copy(content, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            Map<String, Object> localResult = extractLocallyFromTempFile(tempFile, fileName);
            double confidence = (double) localResult.getOrDefault("confidence", 0.0);

            if ("MODE_HYBRID".equals(mode)) {
                try {
                    if (confidence < 0.75) {
                        log.info("Local OCR confidence low ({}), falling back to Gemini for {} with OCR text assistance", confidence, fileName);
                        String rawText = (String) localResult.getOrDefault("raw_text", "");
                        result = extractWithGeminiFromLocalFile(tempFile, fileName, config.getGeminiApiKey(), rawText);
                    } else {
                        result = localResult;
                    }
                } catch (Exception e) {
                    log.warn("Gemini fallback failed for {}, using local OCR result: {}", fileName, e.getMessage(), e);
                    result = localResult;
                    result.put("error", "AI Fallback failed: " + e.getMessage());
                }
            } else {
                result = localResult;
            }

        } catch (Exception e) {
            log.error("OCR extraction failed for file: {}", fileName, e);
            result.put("vendor", extractVendorFromFileName(fileName));
            result.put("amount", 0.0);
            result.put("confidence", 0.1);
            result.put("error", "Extraction error: " + e.getMessage());
            result.put("isValid", false);
            return result;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        boolean hasAmount = result.get("amount") != null && (Double) result.get("amount") > 0;
        boolean hasVendor = result.get("vendor") != null && !result.get("vendor").toString().equalsIgnoreCase("Unknown Vendor");
        boolean hasDate = result.get("date") != null && !result.get("date").toString().isEmpty();

        result.put("isValid", hasAmount && (hasVendor || hasDate));
        if (!(boolean) result.get("isValid")) {
            log.warn("Receipt {} failed validation: Amount: {}, Vendor: {}, Date: {}", fileName, hasAmount, hasVendor, hasDate);
        }

        return result;
    }

    Map<String, Object> extractLocallyFromTempFile(File tempFile, String fileName) throws Exception {
        Map<String, Object> result = new HashMap<>();
        String scriptPath = System.getenv("OCR_SCRIPT_PATH") != null ? System.getenv("OCR_SCRIPT_PATH") : "src/main/resources/scripts/paddle_ocr_processor.py";
        String pythonPath = System.getenv("PYTHON_EXECUTABLE") != null ? System.getenv("PYTHON_EXECUTABLE") : "src/main/resources/scripts/venv/bin/python3";

        ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath, tempFile.getAbsolutePath());
        String scriptDir = new File(scriptPath).getParentFile().getAbsolutePath();
        String hfHome = System.getenv("HF_HOME") != null ? System.getenv("HF_HOME") : scriptDir + "/hf_cache";
        pb.environment().put("HF_HOME", hfHome);
        pb.environment().put("TRANSFORMERS_CACHE", hfHome);
        pb.redirectErrorStream(true);

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Local OCR process timed out");
        }
        
        if (process.exitValue() == 0) {
            result.putAll(parseOcrOutput(output.toString(), fileName));
        } else {
            throw new RuntimeException("Local OCR failed: " + output);
        }
        return result;
    }

    private Map<String, Object> parseOcrOutput(String output, String fileName) {
        Map<String, Object> result = new HashMap<>();
        String rawText = output;
        double confidence = 0.4; // Default low confidence
        
        try {
            int startIndex = output.indexOf("{");
            int endIndex = output.lastIndexOf("}");
            
            if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                String potentialJson = output.substring(startIndex, endIndex + 1);
                JsonNode node = objectMapper.readTree(potentialJson);
                
                if (node.has("text")) {
                    rawText = node.get("text").asText();
                } 
                
                if (node.has("vendor") || node.has("amount")) {
                    result.put("vendor", node.has("vendor") ? node.get("vendor").asText() : extractVendorFromText(rawText, fileName));
                    result.put("amount", node.has("amount") ? node.get("amount").asDouble() : extractAmountFromText(rawText));
                    result.put("date", node.has("date") ? node.get("date").asText() : extractDateFromText(rawText, fileName));
                    result.put("confidence", node.has("confidence") ? node.get("confidence").asDouble() : 0.5);
                    result.put("raw_text", rawText);
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("Mixed output parsing failed, falling back to regex extraction");
        }

        result.put("raw_text", rawText);
        result.put("vendor", extractVendorFromText(rawText, fileName));
        result.put("amount", extractAmountFromText(rawText));
        result.put("date", extractDateFromText(rawText, fileName));
        result.put("confidence", confidence); 
        return result;
    }

    Map<String, Object> extractWithGemini(InputStream content, String fileName, String apiKey) throws Exception {
        File tempFile = File.createTempFile("ocr_gemini_", "_" + fileName);
        Files.copy(content, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
            return extractWithGeminiFromLocalFile(tempFile, fileName, apiKey, null);
        } finally {
            tempFile.delete();
        }
    }

    Map<String, Object> extractWithGeminiFromLocalFile(File file, String fileName, String apiKey, String localOcrText) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equals("your_gemini_api_key_here")) {
            throw new IllegalStateException("Gemini API key is missing.");
        }

        byte[] fileBytes = Files.readAllBytes(file.toPath());
        String base64Data = Base64.getEncoder().encodeToString(fileBytes);
        String mimeType = Files.probeContentType(file.toPath());
        if (mimeType == null) {
            mimeType = fileName.toLowerCase().endsWith(".pdf") ? "application/pdf" : "image/jpeg";
        }

        String prompt = """
            Extract receipt data: vendor, amount (numeric), date (YYYY-MM-DD). 
            
            COUNTERPARTY RULES:
            1. Extract clean merchant/person name.
            2. Remove prefixes like UPI, IMPS, NEFT, POS, CARD and bank names.
            
            Return ONLY a JSON object matching this schema: 
            {
              "vendor":"...",
              "amount":123.45,
              "date":"YYYY-MM-DD",
              "confidenceScore": 0.95,
              "aiReasoning": "Why this vendor was chosen",
              "originalSnippet": "Exact text substring from source"
            }
            """;
        if (localOcrText != null && !localOcrText.trim().isEmpty()) {
            prompt += "\n\nLocal OCR detected the following text (may be partial/noisy):\n" + localOcrText;
            prompt += "\n\nUse this text to assist your high-accuracy extraction from the image.";
        }
        
        String requestBody = String.format("{\"contents\":[{\"parts\":[{\"text\":\"%s\"},{\"inline_data\":{\"mime_type\":\"%s\",\"data\":\"%s\"}}]}]}", 
                prompt.replace("\"", "\\\"").replace("\n", "\\n"), mimeType, base64Data);

        // Throttling to avoid Gemini API rate limits (especially for parallel threads)
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_API_BASE_URL + geminiModel + ":generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            String cleanText = text.replaceAll("```json|```", "").trim();
            Map<String, Object> res = parseOcrOutput(cleanText, fileName);
            res.put("confidence", 1.0);

            // Add to Vendor Dictionary if extraction was successful
            String vendor = (String) res.get("vendor");
            if (vendor != null && !"Unknown Vendor".equalsIgnoreCase(vendor)) {
                // We use a generic tenantId for now or pass it via context if multi-tenancy is active
                vendorDictionaryService.addVendor("local_tenant", vendor, "MAX_ACCURACY_OCR");
            }

            return res;
        } else {
            throw new RuntimeException("Gemini OCR failed: " + response.body());
        }
    }

    private String extractDateFromText(String text, String fileName) {
        if (text != null && !text.isEmpty()) {
            Matcher m1 = Pattern.compile("\\b(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})\\b").matcher(text);
            if (m1.find()) {
                try {
                    return LocalDate.parse(m1.group(), DateTimeFormatter.ofPattern(m1.group().contains("/") ? "yyyy/MM/dd" : "yyyy-MM-dd")).toString();
                } catch (DateTimeParseException e) {
                    log.trace("Failed to parse date {} with pattern 1: {}", m1.group(), e.getMessage());
                }
            }
            Matcher m2 = Pattern.compile("\\b(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})\\b").matcher(text);
            if (m2.find()) {
                try {
                    String sep = m2.group().contains("/") ? "/" : "-";
                    return LocalDate.parse(m2.group(), DateTimeFormatter.ofPattern("dd" + sep + "MM" + sep + "yyyy")).toString();
                } catch (DateTimeParseException e) {
                    log.trace("Failed to parse date {} with pattern 2: {}", m2.group(), e.getMessage());
                }
            }
            Matcher m3 = Pattern.compile("\\b(\\d{1,2})[\\s-]([A-Za-z]{3})[\\s-](\\d{4})\\b").matcher(text);
            if (m3.find()) {
                try {
                    return LocalDate.parse(m3.group().replace(" ", "-"), DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH)).toString();
                } catch (DateTimeParseException e) {
                    log.trace("Failed to parse date {} with pattern 3: {}", m3.group(), e.getMessage());
                }
            }
        }
        Matcher mFile = Pattern.compile("(\\d{4})[^\\d]?(\\d{2})[^\\d]?(\\d{2})?").matcher(fileName);
        if (mFile.find() && mFile.group(1) != null && mFile.group(2) != null) {
            String day = mFile.group(3) != null ? mFile.group(3) : "01";
            try { return LocalDate.parse(mFile.group(1) + "-" + mFile.group(2) + "-" + day).toString(); } catch (Exception e) {
                log.trace("Failed to parse date from filename {}: {}", fileName, e.getMessage());
            }
        }
        return LocalDate.now().toString();
    }

    private String extractVendorFromText(String text, String fileName) {
        if (text == null || text.trim().isEmpty()) return extractVendorFromFileName(fileName);
        String[] lines = text.split("\n");
        for (String line : lines) {
            String clean = line.trim();
            if (clean.isEmpty() || clean.startsWith("Warning:") || clean.contains("Loading weights:") || clean.contains("UserWarning:")) continue;
            if (clean.length() < 3) continue;
            return clean;
        }
        return extractVendorFromFileName(fileName);
    }

    private Double extractAmountFromText(String text) {
        if (text == null) return 0.0;
        Matcher m = Pattern.compile("(?:^|\\s)(?:Rs\\.?|USD|\\$)?\\s?(\\d{1,3}(?:[,\\s]?\\d{3})*(?:[\\.,]\\d{2})?)(?:\\s|$)").matcher(text);
        double maxAmount = 0.0;
        while (m.find()) {
            try {
                String valStr = m.group(1).replace(",", "").replace(" ", "");
                double val = Double.parseDouble(valStr);
                if (val > maxAmount && val < 10000000) maxAmount = val;
            } catch (Exception e) {
                log.trace("Failed to parse currency amount {}: {}", m.group(1), e.getMessage());
            }
        }
        if (maxAmount == 0.0) {
            Matcher mSimple = Pattern.compile("\\b\\d{2,7}\\b").matcher(text);
            while (mSimple.find()) {
                try {
                    double val = Double.parseDouble(mSimple.group());
                    if (val > maxAmount && val < 1000000) maxAmount = val;
                } catch (Exception e) {
                    log.trace("Failed to parse numeric amount {}: {}", mSimple.group(), e.getMessage());
                }
            }
        }
        return maxAmount;
    }

    private String extractVendorFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return "Unknown Vendor";
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        baseName = baseName.replaceAll("[-_]", " ").replaceAll("(?i)\\b(receipt|invoice|bill|scan|doc|pdf|jpg|jpeg|png)\\b", "")
                .replaceAll("\\b\\d{4}[-/\\.]?\\d{2}[-/\\.]?\\d{2}\\b", "").replaceAll("\\b\\d+\\b", "").trim();
        if (baseName.isEmpty()) return "Unknown Vendor";
        String[] words = baseName.split("\\s+");
        StringBuilder titleCase = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) titleCase.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase()).append(" ");
        }
        return titleCase.toString().trim();
    }
}
