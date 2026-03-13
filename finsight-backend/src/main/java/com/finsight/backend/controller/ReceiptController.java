package com.finsight.backend.controller;

import com.finsight.backend.entity.Receipt;
import com.finsight.backend.service.ReceiptService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private static final String DEFAULT_TENANT = "local_tenant";

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @GetMapping
    public ResponseEntity<Page<Receipt>> getReceipts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestHeader(value = "X-Tenant-Id", defaultValue = DEFAULT_TENANT) String tenantId) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(receiptService.getAllReceipts(tenantId, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Receipt> getReceiptById(
            @PathVariable Long id) {
        return ResponseEntity.ok(receiptService.getReceiptById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Receipt> updateReceipt(
            @PathVariable Long id,
            @RequestBody Receipt receipt) {
        return ResponseEntity.ok(receiptService.updateReceipt(id, receipt));
    }
}
