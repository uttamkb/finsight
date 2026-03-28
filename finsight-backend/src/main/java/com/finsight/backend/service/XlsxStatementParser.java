package com.finsight.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.dto.HeaderMetadata;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.entity.ParserPattern;
import com.finsight.backend.util.RuntimeErrorLogger;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Dedicated parser for Excel (.xlsx) bank statements.
 * Uses Apache POI to extract transactions.
 * Handles header detection and robust column mapping.
 */
@Component
public class XlsxStatementParser {

    private static final Logger log = LoggerFactory.getLogger(XlsxStatementParser.class);
    private final CsvStatementParser csvParser;
    private final ParserPatternService patternService;
    private final ObjectMapper objectMapper;

    public XlsxStatementParser(CsvStatementParser csvParser, ParserPatternService patternService, ObjectMapper objectMapper) {
        this.csvParser = csvParser;
        this.patternService = patternService;
        this.objectMapper = objectMapper;
    }

    public List<ParsedBankTransactionDto> parse(File file, String tenantId) throws Exception {
        log.info("XlsxStatementParser: parsing '{}' for tenant '{}'", file.getName(), tenantId);
        List<ParsedBankTransactionDto> results = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            if (rowCount == 0) throw new IllegalArgumentException("Excel file is empty.");

            HeaderMetadata meta = detectHeader(sheet);
            if (meta.getHeaderRowIndex() == -1) {
                throw new IllegalArgumentException("Unable to identify Excel headers.");
            }

            String signature = patternService.generateSignature(meta.getHeaders());
            Optional<ParserPattern> pattern = patternService.findMatchingPattern(tenantId, signature);

            int headerRowIndex = meta.getHeaderRowIndex();
            Map<String, Integer> mapping;

            if (pattern.isPresent()) {
                log.info("Using learned pattern {} (confidence: {})", pattern.get().getPatternGroupId(), pattern.get().getConfidenceScore());
                mapping = objectMapper.readValue(pattern.get().getColumnMapping(), 
                          new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {});
                headerRowIndex = pattern.get().getHeaderRowIndex() != null ? pattern.get().getHeaderRowIndex() : headerRowIndex;
            } else {
                log.info("No pattern found. Using heuristic mapping (confidence: {})", meta.getConfidenceScore());
                mapping = meta.getColumnMapping();
                
                if (meta.getConfidenceScore() >= 60) {
                    patternService.savePattern(tenantId, signature, mapping, 0.8, "XLSX", headerRowIndex);
                }
            }

            int dateIdx = mapping.getOrDefault("date", -1);
            int descIdx = mapping.getOrDefault("description", -1);
            int amountIdx = mapping.getOrDefault("amount", -1);
            int debitIdx = mapping.getOrDefault("debit", -1);
            int creditIdx = mapping.getOrDefault("credit", -1);
            int typeIdx = mapping.getOrDefault("type", -1);

            // --- Step 2: Parse Data Rows ---
            for (int i = headerRowIndex + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    ParsedBankTransactionDto dto = parseRow(row, dateIdx, descIdx, amountIdx, debitIdx, creditIdx, typeIdx);
                    if (dto != null) {
                        results.add(dto);
                    } else {
                        log.debug("Row {}: parseRow returned null (possibly empty or invalid data)", i);
                    }
                } catch (Exception e) {
                    log.error("Row {}: Exception during parsing: {}", i, e.getMessage());
                }
            }
        }

        log.info("XlsxStatementParser: extracted {} rows from '{}'", results.size(), file.getName());
        return results;
    }

    private ParsedBankTransactionDto parseRow(Row row, int dateIdx, int descIdx, int amountIdx, int debitIdx, int creditIdx, int typeIdx) {
        if (dateIdx < 0 || descIdx < 0) {
            log.debug("Row {}: Missing mandatory columns (date or description)", row.getRowNum());
            return null;
        }

        LocalDate txDate = parseDateFromCell(row.getCell(dateIdx));
        if (txDate == null) {
            log.debug("Row {}: Date extraction failed at col {}", row.getRowNum(), dateIdx);
            return null;
        }

        String description = getCellStringValue(row.getCell(descIdx)).trim();
        if (description.isEmpty()) {
            log.debug("Row {}: Description extraction failed at col {}", row.getRowNum(), descIdx);
            return null;
        }

        BigDecimal amount = null;
        String type = null;

        if (amountIdx >= 0) {
            BigDecimal val = getCellNumericValue(row.getCell(amountIdx));
            if (val != null) {
                amount = val.abs();
                
                // Determine type: check explicit type column first, then fall back to sign
                if (typeIdx >= 0) {
                    String typeVal = getCellStringValue(row.getCell(typeIdx)).toLowerCase();
                    if (typeVal.contains("dr") || typeVal.contains("debit") || typeVal.contains("withdrawal")) {
                        type = "DEBIT";
                    } else if (typeVal.contains("cr") || typeVal.contains("credit") || typeVal.contains("deposit")) {
                        type = "CREDIT";
                    }
                }
                
                // Fallback to sign if type column is missing or ambiguous
                if (type == null) {
                    type = val.compareTo(BigDecimal.ZERO) < 0 ? "DEBIT" : "CREDIT";
                }
            } else {
                log.debug("Row {}: Amount extraction failed at col {}", row.getRowNum(), amountIdx);
            }
        } 
        
        if (amount == null) {
            BigDecimal debit = debitIdx >= 0 ? getCellNumericValue(row.getCell(debitIdx)) : null;
            BigDecimal credit = creditIdx >= 0 ? getCellNumericValue(row.getCell(creditIdx)) : null;
            
            if (credit != null && credit.compareTo(BigDecimal.ZERO) > 0) {
                amount = credit; type = "CREDIT";
            } else if (debit != null && debit.compareTo(BigDecimal.ZERO) > 0) {
                amount = debit; type = "DEBIT";
            } else {
                log.debug("Row {}: Both Debit/Credit extraction failed at cols {}/{}", row.getRowNum(), debitIdx, creditIdx);
            }
        }

        if (amount == null || type == null) return null;

        String vendor = description.split("[/*\\-]")[0].trim();
        if (vendor.length() < 3) vendor = description;

        ParsedBankTransactionDto dto = new ParsedBankTransactionDto();
        dto.setTxDate(txDate.toString());
        dto.setDescription(description);
        dto.setVendor(vendor);
        dto.setAmount(amount);
        dto.setType(type);
        return dto;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                type = cell.getCachedFormulaResultType();
            }
            switch (type) {
                case STRING: return cell.getStringCellValue();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toString();
                    return String.valueOf(cell.getNumericCellValue());
                case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                default: return "";
            }
        } catch (Exception e) {
            return "";
        }
    }

    private BigDecimal getCellNumericValue(Cell cell) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                type = cell.getCachedFormulaResultType();
            }
            if (type == CellType.NUMERIC) {
                return BigDecimal.valueOf(cell.getNumericCellValue());
            }
            if (type == CellType.STRING) {
                String val = cell.getStringCellValue().replaceAll("[^0-9.\\-]", "");
                return val.isEmpty() ? null : new BigDecimal(val);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private LocalDate parseDateFromCell(Cell cell) {
        if (cell == null) return null;
        try {
            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                type = cell.getCachedFormulaResultType();
            }
            if (type == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(cell)) {
                    Date date = cell.getDateCellValue();
                    return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                } else {
                    // Handle numeric dates that aren't explicitly formatted (common in some exports)
                    double val = cell.getNumericCellValue();
                    if (val > 20000 && val < 60000) { // Reasonable date range for Excel
                        Date date = DateUtil.getJavaDate(val);
                        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    }
                }
            }
            String val = getCellStringValue(cell);
            LocalDate result = csvParser.parseDateRobustly(val);
            if (result == null && !val.trim().isEmpty()) {
                log.debug("Heuristic date parsing failed for value: '{}'", val);
                RuntimeErrorLogger.logValidation(
                    RuntimeErrorLogger.Module.PARSER,
                    "DateParseFailure",
                    java.util.Map.of("rawCellValue", val, "cellType", cell.getCellType().name()),
                    "A parseable date string",
                    "Could not parse: " + val
                );
            }
            return result;
        } catch (Exception e) {
            log.debug("Failed to parse date from cell: {} - {}", cell, e.getMessage());
            return null;
        }
    }

    public HeaderMetadata detectHeader(Sheet sheet) {
        int headerRowIndex = -1;
        int maxScore = -1;
        Map<String, Integer> mapping = new HashMap<>();
        List<String> detectedHeaders = new ArrayList<>();

        int rowsToCheck = Math.min(sheet.getLastRowNum() + 1, 50); // Check up to 50 rows
        for (int i = 0; i < rowsToCheck; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            int score = 0;
            Map<String, Integer> tMap = new HashMap<>();
            List<String> tHeaders = new ArrayList<>();

            for (int j = 0; j < row.getLastCellNum(); j++) {
                if (j < 0) continue;
                String raw = getCellStringValue(row.getCell(j)).trim();
                tHeaders.add(raw);
                if (raw.isEmpty()) continue;
                String h = getCellStringValue(sheet.getRow(i).getCell(j)).toLowerCase();
                tHeaders.add(h);

                // Date prioritization: "transaction date" > "value date" > "date"
                if (h.contains("transaction date") || h.contains("txn date") || h.contains("tran date")) {
                    tMap.put("date", j); score += 30;
                } else if ((h.contains("value date") || h.contains("vldat")) && !tMap.containsKey("date")) {
                    tMap.put("date", j); score += 25;
                } else if (h.contains("date") && !tMap.containsKey("date")) {
                    tMap.put("date", j); score += 20;
                }

                if (h.contains("remarks") || h.contains("description") || h.contains("particulars") || h.contains("narrative") || h.contains("narration")) {
                    tMap.put("description", j); score += 20;
                }
                else if (h.contains("amount") || h.contains("txn amount") || h.contains("total amount")) { 
                    tMap.put("amount", j); score += 20; 
                }
                else if (h.contains("debit") || h.contains("withdrawal") || h.matches(".*\\bdr\\b.*") || h.contains("payment")) { 
                    tMap.put("debit", j); score += 20; 
                }
                else if (h.contains("credit") || h.contains("deposit") || h.matches(".*\\bcr\\b.*") || h.contains("receipt")) { 
                    tMap.put("credit", j); score += 20; 
                }
                else if (h.equals("type") || h.contains("drcr") || h.contains("txn type")) { 
                    tMap.put("type", j); score += 10; 
                }
            }

            if (score > maxScore && score >= 40) {
                maxScore = score;
                headerRowIndex = i;
                mapping = tMap;
                detectedHeaders = tHeaders;
            }
        }
        
        if (headerRowIndex != -1) {
            log.info("XLSX Header detected at row {}. Mapping: {}", headerRowIndex, mapping);
        } else {
            log.warn("XLSX Header NOT detected in first 50 rows.");
        }

        return HeaderMetadata.builder()
                .headerRowIndex(headerRowIndex)
                .headers(detectedHeaders)
                .columnMapping(mapping)
                .confidenceScore(maxScore)
                .build();
    }
}
