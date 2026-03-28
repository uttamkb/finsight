package com.finsight.backend.controller;

import com.finsight.backend.dto.BankTransactionDto;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.service.BankStatementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/statements")
@CrossOrigin(origins = "*")
@Tag(name = "Bank Statements", description = "Endpoints for uploading statements, processing them via AI, and auto-reconciliation")
public class BankStatementController {

    private static final Logger log = LoggerFactory.getLogger(BankStatementController.class);

    private final BankStatementService bankStatementService;
    private final BankTransactionRepository bankTransactionRepository;

    public BankStatementController(BankStatementService bankStatementService,
                                   BankTransactionRepository bankTransactionRepository) {
        this.bankStatementService = bankStatementService;
        this.bankTransactionRepository = bankTransactionRepository;
    }

    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    @Operation(summary = "Upload Bank Statement", description = "Uploads a PDF or CSV bank statement for AI-based processing and transaction extraction.")
    public ResponseEntity<?> uploadStatement(
            @Parameter(description = "The bank statement file to upload (.pdf, .csv)") @RequestParam("file") MultipartFile file,
            @Parameter(description = "The account type (MAINTENANCE, CORPUS, SINKING_FUND)") @RequestParam(defaultValue = "MAINTENANCE") String accountType) {
        log.info("Received upload request for file: {} and accountType: {}", file.getOriginalFilename(), accountType);
        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            if (file.isEmpty() || (!filename.endsWith(".pdf") && !filename.endsWith(".csv") && !filename.endsWith(".xlsx"))) {
                return ResponseEntity.badRequest().body("Please upload a valid PDF, CSV, or XLSX bank statement.");
            }

            bankStatementService.processStatementAsync("local_tenant", file, accountType);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Bank statement upload initiated.");
            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("Error initiating bank statement upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to initiate processing: " + e.getMessage());
        }
    }

    @GetMapping("/upload/status")
    @Operation(summary = "Get Upload processing status", description = "Polls the current status of the async bank statement processing task.")
    public ResponseEntity<?> getUploadStatus() {
        return ResponseEntity.ok(bankStatementService.getUploadStatus("local_tenant"));
    }

    @GetMapping("/uploads")
    @Operation(summary = "Get Upload History", description = "Returns a list of all bank statement uploads with their status and metrics.")
    public ResponseEntity<?> getUploadHistory() {
        return ResponseEntity.ok(bankStatementService.getRecentUploads("local_tenant"));
    }

    @PostMapping("/uploads/{fileId}/reprocess")
    @Operation(summary = "Reprocess Statement", description = "Triggers reprocessing for a failed or existing statement upload using its fileId.")
    public ResponseEntity<?> reprocessStatement(@PathVariable String fileId) {
        try {
            bankStatementService.reprocessStatement(fileId);
            return ResponseEntity.accepted().body(Map.of("message", "Reprocessing started for " + fileId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get Bank Transactions", description = "Fetches a paginated list of parsed bank transactions.")
    public ResponseEntity<Page<BankTransactionDto>> getTransactions(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "15") int size,
            @Parameter(description = "Filter by reconciled status (true/false)") @RequestParam(required = false) Boolean reconciled,
            @Parameter(description = "Filter by transaction type (DEBIT/CREDIT)") @RequestParam(required = false) String type,
            @Parameter(description = "Filter by start date (YYYY-MM-DD)") @RequestParam(required = false) String startDate,
            @Parameter(description = "Filter by end date (YYYY-MM-DD)") @RequestParam(required = false) String endDate,
            @Parameter(description = "Filter by account type") @RequestParam(defaultValue = "MAINTENANCE") String accountType) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("txDate").descending());
        Page<BankTransactionDto> transactions = bankStatementService.getPagedTransactions("local_tenant", pageRequest, reconciled, type, startDate, endDate, accountType);
        return ResponseEntity.ok(transactions);
    }

    @PutMapping("/transactions/{id}")
    @Operation(summary = "Edit Bank Transaction", description = "Updates fields of an existing parsed bank transaction (e.g., amount, vendor, date) in case of AI extraction errors.")
    public ResponseEntity<BankTransactionDto> updateTransaction(
            @Parameter(description = "Transaction ID") @PathVariable Long id,
            @RequestBody BankTransactionDto updatedDto) {
        try {
            BankTransactionDto savedTxn = bankStatementService.updateTransaction(id, updatedDto);
            return ResponseEntity.ok(savedTxn);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            log.error("Error updating transaction {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
