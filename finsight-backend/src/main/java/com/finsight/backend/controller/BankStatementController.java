package com.finsight.backend.controller;

import com.finsight.backend.dto.BankTransactionDto;
import com.finsight.backend.entity.BankTransaction;
import com.finsight.backend.repository.BankTransactionRepository;
import com.finsight.backend.service.BankStatementService;
import com.finsight.backend.service.ReconciliationService;
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
    private final ReconciliationService reconciliationService;
    private final BankTransactionRepository bankTransactionRepository;

    public BankStatementController(BankStatementService bankStatementService,
                                   ReconciliationService reconciliationService,
                                   BankTransactionRepository bankTransactionRepository) {
        this.bankStatementService = bankStatementService;
        this.reconciliationService = reconciliationService;
        this.bankTransactionRepository = bankTransactionRepository;
    }

    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    @Operation(summary = "Upload Bank Statement", description = "Uploads a PDF or CSV bank statement for AI-based processing and transaction extraction.")
    public ResponseEntity<?> uploadStatement(
            @Parameter(description = "The bank statement file to upload (.pdf, .csv)") @RequestParam("file") MultipartFile file) {
        log.info("Received upload request for file: {}", file.getOriginalFilename());
        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            if (file.isEmpty() || (!filename.endsWith(".pdf") && !filename.endsWith(".csv"))) {
                return ResponseEntity.badRequest().body("Please upload a valid PDF or CSV bank statement.");
            }

            bankStatementService.processStatementAsync("local_tenant", file);
            
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

    @GetMapping("/transactions")
    @Operation(summary = "Get Bank Transactions", description = "Fetches a paginated list of parsed bank transactions.")
    public ResponseEntity<Page<BankTransactionDto>> getTransactions(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "15") int size,
            @Parameter(description = "Filter by reconciled status (true/false)") @RequestParam(required = false) Boolean reconciled) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("txDate").descending());
        Page<BankTransactionDto> transactions = bankStatementService.getPagedTransactions("local_tenant", pageRequest, reconciled);
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

    @PostMapping("/reconcile")
    @Operation(summary = "Trigger Auto-Reconciliation", description = "Manually triggers the auto-reconciliation engine to link unlinked transactions with receipts.")
    public ResponseEntity<?> triggerManualReconciliation() {
        try {
            int reconciledCount = reconciliationService.runReconciliation();
            return ResponseEntity.ok(Map.of("reconciledCount", reconciledCount, "message", "Reconciliation completed."));
        } catch (Exception e) {
            log.error("Error during manual reconciliation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Reconciliation failed.");
        }
    }
}
