package com.finsight.backend.service;

import com.finsight.backend.dto.ParsedBankTransactionDto;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.entity.Category;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Transactional
    public int processPdfStatement(MultipartFile file) throws Exception {
        log.info("Processing Bank Statement PDF: {}", file.getOriginalFilename());

        // 1. Invoke Gemini to parse the PDF and extract JSON data
        List<ParsedBankTransactionDto> parsedTxns = geminiStatementParserService.parsePdfStatement(file);
        
        log.info("Gemini successfully extracted {} transactions from the PDF.", parsedTxns.size());

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
            txn.setTxDate(dto.getTxDate());
            txn.setDescription(dto.getDescription());
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
        String headerLine = null;
        String line;
        // Skip blank lines / comment lines until we find the header
        while ((line = reader.readLine()) != null) {
            if (!line.trim().isEmpty() && !line.trim().startsWith("#")) {
                headerLine = line;
                break;
            }
        }
        if (headerLine == null) throw new IllegalArgumentException("CSV file is empty.");

        // Detect delimiter
        String delimiter = headerLine.contains(";") ? ";" : ",";
        String[] headers = splitCsv(headerLine, delimiter);

        // Map header names to column indices (case-insensitive)
        int dateIdx = -1, descIdx = -1, amountIdx = -1, debitIdx = -1, creditIdx = -1;
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase().replaceAll("[^a-z ]", "");
            if (h.matches("date|txdate|transaction date|value date|posting date")) dateIdx = i;
            else if (h.matches("description|narration|particulars|remarks|details|cheque details")) descIdx = i;
            else if (h.matches("amount|transaction amount")) amountIdx = i;
            else if (h.matches("debit|withdrawal|withdrawal amt")) debitIdx = i;
            else if (h.matches("credit|deposit|deposit amt")) creditIdx = i;
        }

        if (dateIdx < 0 || descIdx < 0 || (amountIdx < 0 && debitIdx < 0 && creditIdx < 0)) {
            throw new IllegalArgumentException(
                "CSV missing required columns. Need: Date, Description/Narration, Amount/Debit/Credit.");
        }

        List<DateTimeFormatter> dateFormats = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy")
        );

        int savedCount = 0;
        int rowNum = 1;
        while ((line = reader.readLine()) != null) {
            rowNum++;
            if (line.trim().isEmpty()) continue;
            String[] cols = splitCsv(line, delimiter);
            if (cols.length <= Math.max(dateIdx, descIdx)) continue;

            try {
                // Parse date
                String rawDate = cols[dateIdx].trim();
                LocalDate txDate = null;
                for (DateTimeFormatter fmt : dateFormats) {
                    try { txDate = LocalDate.parse(rawDate, fmt); break; } catch (DateTimeParseException ignored) {}
                }
                if (txDate == null) {
                    log.warn("Row {}: Cannot parse date '{}', skipping.", rowNum, rawDate);
                    continue;
                }

                String description = cols[descIdx].trim();
                if (description.isEmpty()) continue;

                // Resolve amount and type
                BigDecimal amount = null;
                BankTransaction.TransactionType type = null;

                if (amountIdx >= 0 && amountIdx < cols.length) {
                    String raw = cols[amountIdx].trim().replaceAll("[,₹$£€\\s]", "");
                    if (!raw.isEmpty()) {
                        amount = new BigDecimal(raw).abs();
                        type = amount.compareTo(BigDecimal.ZERO) < 0
                            ? BankTransaction.TransactionType.CREDIT
                            : BankTransaction.TransactionType.DEBIT;
                        amount = amount.abs();
                    }
                } else {
                    BigDecimal debit = BigDecimal.ZERO, credit = BigDecimal.ZERO;
                    if (debitIdx >= 0 && debitIdx < cols.length) {
                        String raw = cols[debitIdx].trim().replaceAll("[,₹$£€\\s]", "");
                        if (!raw.isEmpty()) debit = new BigDecimal(raw);
                    }
                    if (creditIdx >= 0 && creditIdx < cols.length) {
                        String raw = cols[creditIdx].trim().replaceAll("[,₹$£€\\s]", "");
                        if (!raw.isEmpty()) credit = new BigDecimal(raw);
                    }
                    if (debit.compareTo(BigDecimal.ZERO) > 0) {
                        amount = debit; type = BankTransaction.TransactionType.DEBIT;
                    } else if (credit.compareTo(BigDecimal.ZERO) > 0) {
                        amount = credit; type = BankTransaction.TransactionType.CREDIT;
                    }
                }

                if (amount == null || type == null) {
                    log.warn("Row {}: No valid amount found, skipping.", rowNum);
                    continue;
                }

                BankTransaction txn = new BankTransaction();
                txn.setTxDate(txDate);
                txn.setDescription(description);
                txn.setAmount(amount);
                txn.setType(type);

                String rawRef = txDate + "|" + amount + "|" + description;
                txn.setReferenceNumber(Integer.toHexString(rawRef.hashCode()));

                boolean exists = bankTransactionRepository.existsByReferenceNumberAndTenantId(
                    txn.getReferenceNumber(), txn.getTenantId());
                if (!exists) {
                    bankTransactionRepository.save(txn);
                    savedCount++;
                } else {
                    log.debug("Skipped duplicate CSV row: {}", description);
                }

            } catch (Exception e) {
                log.warn("Row {}: Failed to parse - {}. Cause: {}", rowNum, line, e.getMessage());
            }
        }

        log.info("CSV processing complete. Saved {} new transactions.", savedCount);
        return savedCount;
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
