package com.finsight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.dto.HeaderMetadata;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.entity.ParserPattern;
import com.finsight.backend.util.RuntimeErrorLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Dedicated parser for CSV bank statements.
 * Handles auto-detection of: delimiter (comma, semicolon, tab), header row,
 * column mapping (date/description/amount/debit/credit), and multi-format date parsing.
 * Returns clean {@link ParsedBankTransactionDto} objects — no DB access.
 */
@Component
public class CsvStatementParser {
    private static final Logger log = LoggerFactory.getLogger(CsvStatementParser.class);
    private final ParserPatternService patternService;
    private final ObjectMapper objectMapper;

    public CsvStatementParser(ParserPatternService patternService, ObjectMapper objectMapper) {
        this.patternService = patternService;
        this.objectMapper = objectMapper;
    }

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd/MMM/yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d/MMM/yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm:ss a", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy h:mm:ss a", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MM-yyyy h:mm a", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH)
    );

    public List<ParsedBankTransactionDto> parse(File file, String tenantId) throws Exception {
        log.info("CsvStatementParser: parsing '{}' for tenant '{}'", file.getName(), tenantId);

        List<String> allLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null && allLines.size() < 10_000) {
                if (!line.trim().isEmpty()) allLines.add(line);
            }
        }
        if (allLines.isEmpty()) throw new IllegalArgumentException("CSV file is empty.");

        HeaderMetadata meta = detectHeader(allLines);
        if (meta.getHeaderRowIndex() == -1) {
            throw new IllegalArgumentException("Unable to identify CSV headers.");
        }

        String signature = patternService.generateSignature(meta.getHeaders());
        Optional<ParserPattern> pattern = patternService.findMatchingPattern(tenantId, signature);

        int headerLineIndex = meta.getHeaderRowIndex();
        String delimiter = meta.getDelimiter();
        Map<String, Integer> mapping;

        if (pattern.isPresent()) {
            log.info("Using learned pattern {} (confidence: {})", pattern.get().getPatternGroupId(), pattern.get().getConfidenceScore());
            mapping = objectMapper.readValue(pattern.get().getColumnMapping(), 
                      new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {});
            headerLineIndex = pattern.get().getHeaderRowIndex() != null ? pattern.get().getHeaderRowIndex() : headerLineIndex;
        } else {
            log.info("No pattern found. Using heuristic mapping (confidence: {})", meta.getConfidenceScore());
            mapping = meta.getColumnMapping();
            
            if (meta.getConfidenceScore() >= 60) {
                patternService.savePattern(tenantId, signature, mapping, 0.8, "CSV", headerLineIndex);
            }
        }

        int dateIdx = mapping.getOrDefault("date", -1);
        int descIdx = mapping.getOrDefault("description", -1);
        int amountIdx = mapping.getOrDefault("amount", -1);
        int debitIdx = mapping.getOrDefault("debit", -1);
        int creditIdx = mapping.getOrDefault("credit", -1);

        // --- Step 3: Parse data rows ---
        List<ParsedBankTransactionDto> results = new ArrayList<>();
        for (int i = headerLineIndex + 1; i < allLines.size(); i++) {
            ParsedBankTransactionDto dto = parseRow(allLines.get(i), i, delimiter, dateIdx, descIdx, amountIdx, debitIdx, creditIdx);
            if (dto != null) results.add(dto);
        }

        log.info("CsvStatementParser: extracted {} rows from '{}'", results.size(), file.getName());
        return results;
    }

    /** Parses a single CSV data row into a DTO. Returns null if the row should be skipped. */
    private ParsedBankTransactionDto parseRow(String line, int rowNum, String delimiter,
                                              int dateIdx, int descIdx, int amountIdx,
                                              int debitIdx, int creditIdx) {
        if (line.trim().isEmpty()) return null;
        String[] cols = splitCsv(line, delimiter);
        if (cols.length <= Math.max(dateIdx, descIdx)) return null;

        try {
            String rawDate = cols[dateIdx].trim();
            LocalDate txDate = parseDateRobustly(rawDate);
            if (txDate == null) {
                log.warn("Row {}: Cannot parse date '{}', skipping.", rowNum, rawDate);
                RuntimeErrorLogger.logValidation(
                    RuntimeErrorLogger.Module.PARSER,
                    "DateParseFailure",
                    java.util.Map.of("rawDate", rawDate, "row", String.valueOf(rowNum)),
                    "A parseable date string",
                    "Could not parse: " + rawDate
                );
                return null;
            }

            String description = descIdx < cols.length ? cols[descIdx].trim() : "";
            if (description.isEmpty()) return null;

            BigDecimal amount = null;
            String type = null;

            if (amountIdx >= 0 && amountIdx < cols.length) {
                String raw = cols[amountIdx].trim().replaceAll("[^0-9.\\-]", "");
                if (!raw.isEmpty()) {
                    BigDecimal val = new BigDecimal(raw);
                    amount = val.abs();
                    type = val.compareTo(BigDecimal.ZERO) < 0 ? "DEBIT" : "CREDIT";
                }
            } else {
                BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
                if (debitIdx >= 0 && debitIdx < cols.length) {
                    String raw = cols[debitIdx].trim().replaceAll("[^0-9.\\-]", "");
                    if (!raw.isEmpty()) debit = new BigDecimal(raw).abs();
                }
                if (creditIdx >= 0 && creditIdx < cols.length) {
                    String raw = cols[creditIdx].trim().replaceAll("[^0-9.\\-]", "");
                    if (!raw.isEmpty()) credit = new BigDecimal(raw).abs();
                }
                if (credit.compareTo(BigDecimal.ZERO) > 0) { amount = credit; type = "CREDIT"; }
                else if (debit.compareTo(BigDecimal.ZERO) > 0) { amount = debit; type = "DEBIT"; }
            }

            if (amount == null || type == null) return null;

            // Simple vendor extraction: take the prefix before any split character
            String vendor = description.split("[/*\\-]")[0].trim();
            if (vendor.length() < 3) vendor = description;

            ParsedBankTransactionDto dto = new ParsedBankTransactionDto();
            dto.setTxDate(txDate.toString());
            dto.setDescription(description);
            dto.setVendor(vendor);
            dto.setAmount(amount);
            dto.setType(type);
            return dto;

        } catch (Exception e) {
            log.warn("Row {}: Failed to parse - {}. Cause: {}", rowNum, line, e.getMessage());
            return null;
        }
    }

    public HeaderMetadata detectHeader(List<String> allLines) {
        int headerLineIndex = -1;
        int maxScore = -1;
        String delimiter = ",";
        Map<String, Integer> mapping = new HashMap<>();
        List<String> detectedHeaders = new ArrayList<>();

        for (int i = 0; i < Math.min(30, allLines.size()); i++) {
            String cur = allLines.get(i);
            String delim = cur.contains(";") ? ";" : cur.contains("\t") ? "\t" : ",";
            String[] parts = splitCsv(cur, delim);

            int score = 0;
            Map<String, Integer> tMap = new HashMap<>();
            List<String> tHeaders = new ArrayList<>();
            
            for (int j = 0; j < parts.length; j++) {
                String raw = parts[j].trim();
                tHeaders.add(raw);
                if (raw.isEmpty()) continue;
                String h = raw.toLowerCase().replaceAll("[^a-z ]", "");
                if (h.contains("date") || h.contains("tx date") || h.contains("value date") || h.contains("txn date")) { tMap.put("date", j); score += 20; }
                else if (h.contains("description") || h.contains("narration") || h.contains("particulars") || h.contains("details") || h.contains("remarks")) { tMap.put("description", j); score += 20; }
                else if (h.contains("amount") || h.contains("txn amount") || h.contains("transaction amount")) { tMap.put("amount", j); score += 15; }
                else if (h.contains("debit") || h.contains("withdrawal") || h.matches(".*\\bdr\\b.*")) { tMap.put("debit", j); score += 10; }
                else if (h.contains("credit") || h.contains("deposit") || h.matches(".*\\bcr\\b.*")) { tMap.put("credit", j); score += 10; }
                else if (h.matches("balance|closing balance")) { score += 5; }
            }

            if (score > maxScore && score >= 40) {
                maxScore = score;
                headerLineIndex = i;
                delimiter = delim;
                mapping = tMap;
                detectedHeaders = tHeaders;
            }
        }

        return HeaderMetadata.builder()
                .headerRowIndex(headerLineIndex)
                .headers(detectedHeaders)
                .columnMapping(mapping)
                .confidenceScore(maxScore)
                .delimiter(delimiter)
                .build();
    }

    /** Tries all known date formats until one succeeds. Returns null if none match. */
    public LocalDate parseDateRobustly(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) return null;
        String clean = rawDate.trim().replaceAll("\\s+", " ");
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(clean, fmt); } catch (Exception ignored) {}
        }
        try {
            if (clean.contains("T")) return LocalDate.parse(clean.split("T")[0]);
        } catch (Exception ignored) {}
        return null;
    }

    /** Splits a CSV line respecting double-quoted fields. */
    private String[] splitCsv(String line, String delimiter) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (!inQuotes && line.startsWith(delimiter, i)) {
                result.add(current.toString());
                current.setLength(0);
                i += delimiter.length() - 1;
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }
}
