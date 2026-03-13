package com.finsight.backend.service;

import com.finsight.backend.dto.BankTransactionDto;
import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Category;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Service
public class BankStatementService {

    private static final Logger log = LoggerFactory.getLogger(BankStatementService.class);

    private final GeminiStatementParserService geminiStatementParserService;
    private final BankTransactionRepository bankTransactionRepository;
    private final CategoryRepository categoryRepository;

    public BankStatementService(GeminiStatementParserService geminiStatementParserService,
                                BankTransactionRepository bankTransactionRepository,
                                CategoryRepository categoryRepository) {
        this.geminiStatementParserService = geminiStatementParserService;
        this.bankTransactionRepository = bankTransactionRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Page<BankTransactionDto> getPagedTransactions(String tenantId, Pageable pageable) {
        return bankTransactionRepository.findByTenantIdWithCategory(tenantId, pageable)
                .map(BankTransactionDto::from);
    }

    @Transactional
    public int processPdfStatement(MultipartFile file) throws Exception {
        log.info("Processing Bank Statement PDF: {}", file.getOriginalFilename());

        // 1. Invoke Gemini to parse the PDF and extract JSON data
        List<ParsedBankTransactionDto> parsedTxns = geminiStatementParserService.parsePdfStatement(file);
        
        log.info("Parser successfully extracted {} transactions from the PDF.", parsedTxns.size());

        int savedCount = 0;

        // 2. Map DTOs to Entities and Save
        for (ParsedBankTransactionDto dto : parsedTxns) {
            
            // Skip invalid data
            if (dto.getTxDate() == null || dto.getAmount() == null || dto.getDescription() == null) {
                log.warn("Skipping invalid transaction row parsed by Gemini. Missing mandatory fields: {}", dto.getDescription());
                continue;
            }

            // Create Entity
            BankTransaction txn = new BankTransaction();
            
            // Normalize Date
            LocalDate txDate = parseDateRobustly(dto.getTxDate());
            if (txDate == null) {
                log.warn("Skipping row: Cannot parse date '{}'", dto.getTxDate());
                continue;
            }
            txn.setTxDate(txDate);
            txn.setDescription(dto.getDescription());
            txn.setVendor(dto.getVendor() != null ? dto.getVendor() : "Unknown"); // Set vendor from DTO
            txn.setAmount(dto.getAmount());
            
            // Map String Type to Enum
            try {
                txn.setType(BankTransaction.TransactionType.valueOf(dto.getType().toUpperCase()));
            } catch (Exception e) {
                log.warn("Invalid transaction type ({}) parsed by Gemini. Defaulting to DEBIT.", dto.getType());
                txn.setType(BankTransaction.TransactionType.DEBIT);
            }

            // Handle Categorization
            if (dto.getCategory() != null && !dto.getCategory().trim().isEmpty()) {
                Category category = getOrCreateCategory(dto.getCategory().trim(),
                        txn.getType() == BankTransaction.TransactionType.CREDIT ? Category.CategoryType.INCOME : Category.CategoryType.EXPENSE);
                txn.setCategory(category);
            }

            // Generate a reference number hash to prevent duplicates (simplified for MVP)
            String rawRef = txn.getTxDate() + "|" + txn.getAmount() + "|" + txn.getDescription();
            txn.setReferenceNumber(Integer.toHexString(rawRef.hashCode()));

            // Basic dedup check
            boolean exists = bankTransactionRepository.existsByReferenceNumberAndTenantId(txn.getReferenceNumber(), txn.getTenantId());
            if (!exists) {
                bankTransactionRepository.save(txn);
                savedCount++;
            } else {
                log.debug("Skipped duplicate transaction: {}", dto.getDescription());
            }
        }

        log.info("Successfully persisted {} new transactions to the database.", savedCount);
        return savedCount;
    }

    private Category getOrCreateCategory(String name, Category.CategoryType type) {
        return categoryRepository.findByNameAndTenantId(name, "local_tenant")
                .orElseGet(() -> {
                    Category c = new Category();
                    c.setName(name);
                    c.setType(type);
                    return categoryRepository.save(c);
                });
    }

    /**
     * Processes a CSV bank statement by parsing rows natively (no extra dependencies).
     * Supports comma and semicolon delimiters. Handles combined Amount or split Debit/Credit columns.
     * Common column aliases (case-insensitive):
     *   Date: date, txdate, transaction date, value date
     *   Description: description, narration, particulars, remarks, details
     *   Amount: amount, withdrawal amt, deposit amt, debit, credit
     */
    @Transactional
    public int processCsvStatement(MultipartFile file) throws Exception {
        log.info("Processing Bank Statement CSV: {}", file.getOriginalFilename());

        BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
        List<String> allLines = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null && allLines.size() < 100) {
            if (!line.trim().isEmpty()) allLines.add(line);
        }
        if (allLines.isEmpty()) throw new IllegalArgumentException("CSV file is empty.");

        // Identify the header line by scanning the first ~20 lines
        int headerLineIndex = -1;
        int maxScore = -1;
        String detectedDelimiter = ",";
        int dateIdx = -1, descIdx = -1, amountIdx = -1, debitIdx = -1, creditIdx = -1;

        for (int i = 0; i < Math.min(20, allLines.size()); i++) {
            String currentLine = allLines.get(i);
            String currentDelimiter = currentLine.contains(";") ? ";" : 
                                      currentLine.contains("\t") ? "\t" : ",";
            String[] parts = splitCsv(currentLine, currentDelimiter);
            
            int score = 0;
            int tempDate = -1, tempDesc = -1, tempAmt = -1, tempDeb = -1, tempCre = -1;
            
            for (int j = 0; j < parts.length; j++) {
                String h = parts[j].trim().toLowerCase().replaceAll("[^a-z ]", "");
                if (h.matches("date|txdate|transaction date|value date|posting date")) { tempDate = j; score += 20; }
                else if (h.matches("description|narration|particulars|remarks|details|chq details|naration")) { tempDesc = j; score += 20; }
                else if (h.matches("amount|txn amount|transaction amount")) { tempAmt = j; score += 15; }
                else if (h.matches("debit|withdrawal|withdrawal amt|dr")) { tempDeb = j; score += 10; }
                else if (h.matches("credit|deposit|deposit amt|cr")) { tempCre = j; score += 10; }
                else if (h.matches("balance|closing balance")) { score += 5; }
            }

            if (score > maxScore && score >= 40) { // Require at least Date + Desc or similar
                maxScore = score;
                headerLineIndex = i;
                detectedDelimiter = currentDelimiter;
                dateIdx = tempDate;
                descIdx = tempDesc;
                amountIdx = tempAmt;
                debitIdx = tempDeb;
                creditIdx = tempCre;
            }
        }

        if (headerLineIndex == -1) {
            throw new IllegalArgumentException(
                "Unable to identify CSV headers. Ensure column names like 'Date', 'Description', and 'Amount' are present.");
        }

        log.info("Detected header at line {} with delimiter '{}'. Score: {}", headerLineIndex, detectedDelimiter, maxScore);


        List<DateTimeFormatter> dateFormats = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("MMM dd, yyyy")
        );

        int savedCount = 0;
        int rowIdx = 0;
        
        // 1. Process lines cached for header detection
        for (int i = headerLineIndex + 1; i < allLines.size(); i++) {
            if (processRow(allLines.get(i), rowIdx++, detectedDelimiter, dateIdx, descIdx, amountIdx, debitIdx, creditIdx)) {
                savedCount++;
            }
        }
        
        // 2. Process remaining lines in stream
        while ((line = reader.readLine()) != null) {
            if (processRow(line, rowIdx++, detectedDelimiter, dateIdx, descIdx, amountIdx, debitIdx, creditIdx)) {
                savedCount++;
            }
        }
        
        log.info("CSV processing complete. Saved {} new transactions.", savedCount);
        return savedCount;
    }

    private boolean processRow(String line, int rowNum, String delimiter, int dateIdx, int descIdx, int amountIdx, int debitIdx, int creditIdx) {
        if (line.trim().isEmpty()) return false;
        String[] cols = splitCsv(line, delimiter);
        if (cols.length <= Math.max(dateIdx, descIdx)) return false;

        try {
            // Parse date
            String rawDate = cols[dateIdx].trim();
            LocalDate txDate = parseDateRobustly(rawDate);
            
            if (txDate == null) {
                log.warn("Row {}: Cannot parse date '{}', skipping.", rowNum, rawDate);
                return false;
            }

            String description = cols[descIdx].trim();
            if (description.isEmpty()) return false;

            // Resolve amount and type
            BigDecimal amount = null;
            BankTransaction.TransactionType type = null;

            if (amountIdx >= 0 && amountIdx < cols.length) {
                String raw = cols[amountIdx].trim().replaceAll("[^0-9\\.-]", "");
                if (!raw.isEmpty()) {
                    try {
                        BigDecimal val = new BigDecimal(raw);
                        amount = val.abs();
                        type = val.compareTo(BigDecimal.ZERO) < 0
                            ? BankTransaction.TransactionType.DEBIT
                            : BankTransaction.TransactionType.CREDIT;
                    } catch (Exception e) {
                        log.warn("Row {}: Invalid amount format '{}'", rowNum, raw);
                    }
                }
            } else {
                BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
                if (debitIdx >= 0 && debitIdx < cols.length) {
                    String raw = cols[debitIdx].trim().replaceAll("[^0-9\\.-]", "");
                    if (!raw.isEmpty()) debit = new BigDecimal(raw).abs();
                }
                if (creditIdx >= 0 && creditIdx < cols.length) {
                    String raw = cols[creditIdx].trim().replaceAll("[^0-9\\.-]", "");
                    if (!raw.isEmpty()) credit = new BigDecimal(raw).abs();
                }
                
                if (credit.compareTo(BigDecimal.ZERO) > 0) {
                    amount = credit; type = BankTransaction.TransactionType.CREDIT;
                } else if (debit.compareTo(BigDecimal.ZERO) > 0) {
                    amount = debit; type = BankTransaction.TransactionType.DEBIT;
                }
            }

            if (amount == null || type == null) {
                return false;
            }

            // Vendor Detection Logic (Step 6)
            // For CSV, the description is often the vendor/payee
            // We can add simple NLP or keyword matching here later

            BankTransaction txn = new BankTransaction();
            txn.setTxDate(txDate);
            txn.setDescription(description);
            
            // Simple CSV Vendor Detection (Step 6)
            String vendor = description.split("[/\\*\\-]")[0].trim();
            if (vendor.length() < 3) vendor = description;
            txn.setVendor(vendor);

            txn.setAmount(amount);
            txn.setType(type);

            String rawRef = txDate + "|" + amount + "|" + description;
            txn.setReferenceNumber(Integer.toHexString(rawRef.hashCode()));

            boolean exists = bankTransactionRepository.existsByReferenceNumberAndTenantId(
                txn.getReferenceNumber(), txn.getTenantId());
            if (!exists) {
                bankTransactionRepository.save(txn);
                return true;
            } else {
                log.debug("Skipped duplicate CSV row: {}", description);
                return false;
            }

        } catch (Exception e) {
            log.warn("Row {}: Failed to parse - {}. Cause: {}", rowNum, line, e.getMessage());
            return false;
        }
    }

    public LocalDate parseDateRobustly(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) return null;
        
        List<DateTimeFormatter> dateFormats = List.of(
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
            DateTimeFormatter.ofPattern("d-MMM-yyyy", java.util.Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM, yyyy", java.util.Locale.ENGLISH)
        );

        String cleanDate = rawDate.trim().replaceAll("\\s+", " ");
        for (DateTimeFormatter fmt : dateFormats) {
            try {
                return LocalDate.parse(cleanDate, fmt);
            } catch (Exception e) {
                // Try next
            }
        }
        
        // Manual fallback for very strange formats or timestamps
        try {
            if (cleanDate.contains("T")) {
                return LocalDate.parse(cleanDate.split("T")[0]);
            }
        } catch (Exception e) {}

        return null;
    }

    /** Splits a CSV line respecting quoted fields. */
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
