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

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/statements")
@CrossOrigin(origins = "*")
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

    @PostMapping("/upload")
    public ResponseEntity<?> uploadStatement(@RequestParam("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            if (file.isEmpty() || (!filename.endsWith(".pdf") && !filename.endsWith(".csv"))) {
                return ResponseEntity.badRequest().body("Please upload a valid PDF or CSV bank statement.");
            }

            int savedCount;
            if (filename.endsWith(".csv")) {
                savedCount = bankStatementService.processCsvStatement(file);
            } else {
                savedCount = bankStatementService.processPdfStatement(file);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Bank statement processed successfully.");
            response.put("transactionsSaved", savedCount);
            response.put("manualReconcileRequired", true);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid CSV format: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error processing bank statement", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to process statement: " + e.getMessage());
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<Page<BankTransactionDto>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("txDate").descending());
        Page<BankTransactionDto> transactions = bankStatementService.getPagedTransactions("local_tenant", pageRequest);
        return ResponseEntity.ok(transactions);
    }

    @PostMapping("/reconcile")
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
