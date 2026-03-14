package com.finsight.backend.service;

import com.finsight.backend.dto.ParsedBankTransactionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated parser for CSV bank statements.
 * Handles auto-detection of: delimiter (comma, semicolon, tab), header row,
 * column mapping (date/description/amount/debit/credit), and multi-format date parsing.
 * Returns clean {@link ParsedBankTransactionDto} objects — no DB access.
 */
@Component
public class CsvStatementParser {

    private static final Logger log = LoggerFactory.getLogger(CsvStatementParser.class);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy"),
        DateTimeFormatter.ofPattern("dd MMM yyyy"),
        DateTimeFormatter.ofPattern("d MMM yyyy"),
        DateTimeFormatter.ofPattern("MMM dd, yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yy"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM, yyyy", Locale.ENGLISH)
    );

    /**
     * Parses a CSV multipart file and returns a list of raw parsed transactions.
     * Limits input to 10,000 non-empty lines to prevent memory exhaustion.
     */
    public List<ParsedBankTransactionDto> parse(MultipartFile file) throws Exception {
        log.info("CsvStatementParser: parsing '{}'", file.getOriginalFilename());

        // --- Step 1: Read up to 10,000 non-empty lines ---
        List<String> allLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null && allLines.size() < 10_000) {
                if (!line.trim().isEmpty()) allLines.add(line);
            }
        }
        if (allLines.isEmpty()) throw new IllegalArgumentException("CSV file is empty.");

        // --- Step 2: Header detection (scan first 20 lines, pick best scoring candidate) ---
        int headerLineIndex = -1;
        int maxScore = -1;
        String delimiter = ",";
        int dateIdx = -1, descIdx = -1, amountIdx = -1, debitIdx = -1, creditIdx = -1;

        for (int i = 0; i < Math.min(20, allLines.size()); i++) {
            String cur = allLines.get(i);
            String delim = cur.contains(";") ? ";" : cur.contains("\t") ? "\t" : ",";
            String[] parts = splitCsv(cur, delim);

            int score = 0;
            int td = -1, tDesc = -1, tAmt = -1, tDeb = -1, tCre = -1;
            for (int j = 0; j < parts.length; j++) {
                String h = parts[j].trim().toLowerCase().replaceAll("[^a-z ]", "");
                if (h.matches("date|txdate|transaction date|value date|posting date")) { td = j; score += 20; }
                else if (h.matches("description|narration|particulars|remarks|details|chq details|naration")) { tDesc = j; score += 20; }
                else if (h.matches("amount|txn amount|transaction amount")) { tAmt = j; score += 15; }
                else if (h.matches("debit|withdrawal|withdrawal amt|dr")) { tDeb = j; score += 10; }
                else if (h.matches("credit|deposit|deposit amt|cr")) { tCre = j; score += 10; }
                else if (h.matches("balance|closing balance")) { score += 5; }
            }

            if (score > maxScore && score >= 40) {
                maxScore = score; headerLineIndex = i; delimiter = delim;
                dateIdx = td; descIdx = tDesc; amountIdx = tAmt; debitIdx = tDeb; creditIdx = tCre;
            }
        }

        if (headerLineIndex == -1) {
            throw new IllegalArgumentException(
                "Unable to identify CSV headers. Ensure column names like 'Date', 'Description', and 'Amount' are present.");
        }
        log.info("Header detected at line {} with delimiter '{}'. Score={}", headerLineIndex, delimiter, maxScore);

        // --- Step 3: Parse data rows ---
        List<ParsedBankTransactionDto> results = new ArrayList<>();
        for (int i = headerLineIndex + 1; i < allLines.size(); i++) {
            ParsedBankTransactionDto dto = parseRow(allLines.get(i), i, delimiter, dateIdx, descIdx, amountIdx, debitIdx, creditIdx);
            if (dto != null) results.add(dto);
        }

        log.info("CsvStatementParser: extracted {} rows from '{}'", results.size(), file.getOriginalFilename());
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
