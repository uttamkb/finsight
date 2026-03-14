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

        String normalizedName = vendorName.trim();
        if ("Unknown".equalsIgnoreCase(normalizedName) || normalizedName.toLowerCase().contains("unknown vendor")) {
            return;
        }
        Optional<Vendor> optionalVendor = vendorRepository.findByTenantIdAndName(tenantId, normalizedName);

        Vendor vendor;
        if (optionalVendor.isPresent()) {
            vendor = optionalVendor.get();
        } else {
            vendor = new Vendor();
            vendor.setTenantId(tenantId);
            vendor.setName(normalizedName);
            vendor.setTotalSpent(BigDecimal.ZERO);
            vendor.setTotalPayments(0);
            log.info("Created new vendor entry for: {}", normalizedName);
        }

        // Update statistics
        vendor.setTotalSpent(vendor.getTotalSpent().add(amount != null ? amount : BigDecimal.ZERO));
        vendor.setTotalPayments(vendor.getTotalPayments() + 1);
        
        LocalDateTime paymentTime = date != null ? date.atStartOfDay() : LocalDateTime.now();
        if (vendor.getLastPaymentDate() == null || paymentTime.isAfter(vendor.getLastPaymentDate())) {
            vendor.setLastPaymentDate(paymentTime);
        }

        vendorRepository.save(vendor);
        log.debug("Updated vendor stats for {}: Total spent = {}, Payments = {}", 
                  normalizedName, vendor.getTotalSpent(), vendor.getTotalPayments());
    }
}
