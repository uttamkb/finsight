package com.finsight.backend.controller;

import com.finsight.backend.entity.Receipt;
import com.finsight.backend.service.ReceiptService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/receipts")
@Tag(name = "Receipts", description = "Endpoints for managing imported digital and OCR receipts")
public class ReceiptController {

    private static final String DEFAULT_TENANT = "local_tenant";

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping
    @Operation(summary = "Get Receipts", description = "Retrieves a paginated list of ingested receipts.")
    public ResponseEntity<Page<Receipt>> getReceipts(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Search query against receipt content") @RequestParam(required = false) String search,
            @Parameter(description = "Target Tenant ID context") @RequestHeader(value = "X-Tenant-Id", defaultValue = DEFAULT_TENANT) String tenantId) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(receiptService.getAllReceipts(tenantId, search, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Receipt by ID", description = "Retrieves full details of a single valid receipt by ID.")
    public ResponseEntity<Receipt> getReceiptById(
            @Parameter(description = "Receipt primary identifier") @PathVariable Long id) {
        return ResponseEntity.ok(receiptService.getReceiptById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update Receipt", description = "Allows manual correction of extracted details from a specific receipt.")
    public ResponseEntity<Receipt> updateReceipt(
            @Parameter(description = "Receipt primary identifier") @PathVariable Long id,
            @RequestBody Receipt receipt) {
        return ResponseEntity.ok(receiptService.updateReceipt(id, receipt));
    }
}
