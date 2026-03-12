package com.finsight.backend.service;

import com.finsight.backend.entity.Receipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface ReceiptService {
    Page<Receipt> getAllReceipts(String tenantId, String search, Pageable pageable);
    Receipt getReceiptById(Long id);
    Receipt saveReceipt(Receipt receipt);
}
