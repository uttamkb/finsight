package com.finsight.backend.service;

import com.finsight.backend.entity.Vendor;
import com.finsight.backend.repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class VendorManager {

    private static final Logger log = LoggerFactory.getLogger(VendorManager.class);

    private final VendorRepository vendorRepository;

    public VendorManager(VendorRepository vendorRepository) {
        this.vendorRepository = vendorRepository;
    }

    /**
     * Updates or creates a vendor entry and increments its statistics.
     *
     * @param tenantId   The tenant identifier.
     * @param vendorName The name of the vendor (extracted from OCR or description).
     * @param amount     The transaction amount.
     * @param date       The transaction date.
     */
    @Transactional
    public void updateVendorStats(String tenantId, String vendorName, BigDecimal amount, LocalDate date) {
        if (tenantId == null || vendorName == null || vendorName.isBlank() || amount == null) {
            return;
        }

        // ── Normalization to prevent duplicates (e.g., SWIGGY vs Swiggy) ──
        String normalizedNameForLookup = vendorName.trim().toUpperCase();
        if ("UNKNOWN".equalsIgnoreCase(normalizedNameForLookup)) {
            return;
        }

        // Case-insensitive search
        Optional<Vendor> optionalVendor = vendorRepository.findByTenantIdAndNameIgnoreCase(tenantId, normalizedNameForLookup);

        Vendor vendor;
        if (optionalVendor.isPresent()) {
            vendor = optionalVendor.get();
        } else {
            vendor = new Vendor();
            vendor.setTenantId(tenantId);
            // Prettify the name for display (Title Case)
            vendor.setName(toTitleCase(vendorName.trim()));
            vendor.setTotalSpent(BigDecimal.ZERO);
            vendor.setTotalPayments(0);
            log.info("Created new canonical vendor entry for: {}", vendor.getName());
        }

        // Update statistics
        vendor.setTotalSpent(vendor.getTotalSpent().add(amount));
        vendor.setTotalPayments(vendor.getTotalPayments() + 1);
        
        LocalDateTime paymentTime = date != null ? date.atStartOfDay() : LocalDateTime.now();
        if (vendor.getLastPaymentDate() == null || paymentTime.isAfter(vendor.getLastPaymentDate())) {
            vendor.setLastPaymentDate(paymentTime);
        }

        vendorRepository.save(vendor);
    }

    private String toTitleCase(String input) {
        if (input == null || input.isEmpty()) return input;
        StringBuilder result = new StringBuilder();
        boolean nextTitleCase = true;
        for (char c : input.toLowerCase().toCharArray()) {
            if (Character.isSpaceChar(c)) {
                nextTitleCase = true;
            } else if (nextTitleCase) {
                c = Character.toTitleCase(c);
                nextTitleCase = false;
            }
            result.append(c);
        }
        return result.toString();
    }
}
