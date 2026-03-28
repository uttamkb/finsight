package com.finsight.backend.service;

import com.finsight.backend.dto.FileProcessingLog;
import com.finsight.backend.dto.HeaderMetadata;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.dto.TrainingSummaryResponse;
import com.finsight.backend.entity.ParserPattern;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ParserTrainingService {

    private static final Logger log = LoggerFactory.getLogger(ParserTrainingService.class);

    private final CsvStatementParser csvParser;
    private final XlsxStatementParser xlsxParser;
    private final ParserPatternService patternService;
    private final GeminiClient geminiClient;
    private final AppConfigService appConfigService;

    public ParserTrainingService(CsvStatementParser csvParser,
                                 XlsxStatementParser xlsxParser,
                                 ParserPatternService patternService,
                                 GeminiClient geminiClient,
                                 AppConfigService appConfigService) {
        this.csvParser = csvParser;
        this.xlsxParser = xlsxParser;
        this.patternService = patternService;
        this.geminiClient = geminiClient;
        this.appConfigService = appConfigService;
    }

    public TrainingSummaryResponse trainBulk(String tenantId, List<MultipartFile> files) {
        int successful = 0;
        int failed = 0;
        int learned = 0;
        int reused = 0;
        int flagged = 0;
        List<FileProcessingLog> processingLogs = new ArrayList<>();

        String apiKey = null;
        try {
            apiKey = appConfigService.getConfig().getGeminiApiKey();
        } catch (Exception ignored) {}

        for (MultipartFile multipartFile : files) {
            String fileName = multipartFile.getOriginalFilename();
            try {
                Path tempPath = Files.createTempFile("train-", fileName);
                multipartFile.transferTo(tempPath.toFile());
                File file = tempPath.toFile();

                HeaderMetadata meta = detectHeaderForFile(file);
                if (meta == null || meta.getHeaderRowIndex() == -1) {
                    throw new IllegalArgumentException("No header detected in file: " + fileName);
                }

                // AI Assist Fallback
                if (meta.getConfidenceScore() < 40 && apiKey != null) {
                    log.info("Low confidence ({}) for {}. Invoking Gemini AI Assist...", meta.getConfidenceScore(), fileName);
                    Map<String, Integer> aiMapping = suggestMappingAI(meta.getHeaders(), apiKey);
                    if (aiMapping != null && !aiMapping.isEmpty()) {
                        meta.setColumnMapping(aiMapping);
                        meta.setConfidenceScore(75.0); // Boost confidence due to AI validation
                    }
                }

                String signature = patternService.generateSignature(meta.getHeaders());
                Optional<ParserPattern> existing = patternService.findMatchingPattern(tenantId, signature);
                String statusMsg;
                double confidence;

                if (existing.isPresent()) {
                    reused++;
                    statusMsg = "Pattern Reused";
                    confidence = existing.get().getConfidenceScore();
                } else {
                    learned++;
                    statusMsg = "New Pattern Learned";
                    confidence = meta.getConfidenceScore() / 100.0;
                    patternService.savePattern(tenantId, signature, meta.getColumnMapping(), 
                                              confidence, getFormat(fileName), meta.getHeaderRowIndex());
                }

                List<ParsedBankTransactionDto> testRows = parseWithMeta(file, meta, tenantId);
                if (testRows.isEmpty()) {
                    failed++;
                    statusMsg = "CRITICAL FAILURE: 0 Rows Extracted | Mapping likely incorrect for headers: " + meta.getHeaders();
                    log.error("Training failed for {}: 0 rows extracted with mapping {}", fileName, meta.getColumnMapping());
                } else {
                    successful++;
                    log.info("Training success for {}: {} rows extracted. Sample: {}", fileName, testRows.size(), 
                        testRows.size() > 0 ? testRows.get(0).getDescription() + " | " + testRows.get(0).getAmount() : "N/A");
                }

                processingLogs.add(FileProcessingLog.builder()
                        .fileName(fileName)
                        .detectedHeaderRow(meta.getHeaderRowIndex())
                        .signature(signature)
                        .status(statusMsg)
                        .confidenceScore(confidence)
                        .extractedRowCount(testRows.size())
                        .build());

                Files.deleteIfExists(tempPath);
            } catch (Exception e) {
                log.error("Failed to train file: {}", fileName, e);
                failed++;
                processingLogs.add(FileProcessingLog.builder()
                        .fileName(fileName)
                        .status("CRITICAL FAILURE: " + e.getMessage())
                        .build());
            }
        }
        return TrainingSummaryResponse.builder()
                .totalFilesProcessed(files.size())
                .successfulParses(successful)
                .failedParses(failed)
                .patternsLearned(learned)
                .patternsReused(reused)
                .patternsFlaggedForReview(flagged)
                .logs(processingLogs)
                .build();
    }

    private Map<String, Integer> suggestMappingAI(List<String> headers, String apiKey) {
        String prompt = """
            You are an expert in bank statement structures. 
            Identify the column indexes (0-based) for: date, description, amount, debit, credit, type (Dr/Cr).
            Return ONLY a JSON map like: {"date":0, "description":1, "amount":2, ...}.
            Use -1 if a column is missing.
            """;
        try {
            String context = String.join(" | ", headers);
            String response = geminiClient.callGeminiGeneric(prompt, context, apiKey);
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(response, 
                   new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            log.error("AI Mapping Suggestion failed", e);
            return null;
        }
    }

    private HeaderMetadata detectHeaderForFile(File file) throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".csv")) {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                String line;
                while ((line = reader.readLine()) != null && lines.size() < 20) {
                    if (!line.trim().isEmpty()) lines.add(line);
                }
            }
            return csvParser.detectHeader(lines);
        } else if (name.endsWith(".xlsx")) {
            try (Workbook workbook = WorkbookFactory.create(file)) {
                return xlsxParser.detectHeader(workbook.getSheetAt(0));
            }
        }
        return null;
    }

    private List<ParsedBankTransactionDto> parseWithMeta(File file, HeaderMetadata meta, String tenantId) throws Exception {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".csv")) return csvParser.parse(file, tenantId);
        if (name.endsWith(".xlsx")) return xlsxParser.parse(file, tenantId);
        return new ArrayList<>();
    }

    private String getFormat(String fileName) {
        if (fileName.toLowerCase().endsWith(".csv")) return "CSV";
        if (fileName.toLowerCase().endsWith(".xlsx")) return "XLSX";
        return "UNKNOWN";
    }
}
