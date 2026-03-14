package com.finsight.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.entity.VendorDictionary;
import com.finsight.backend.repository.VendorDictionaryRepository;
import com.finsight.backend.service.VendorDictionaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class VendorDictionaryServiceImpl implements VendorDictionaryService {
    private static final Logger log = LoggerFactory.getLogger(VendorDictionaryServiceImpl.class);
    private static final String EXPORT_PATH = "src/main/resources/scripts/vendor_dictionary.json";
    
    private final VendorDictionaryRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VendorDictionaryServiceImpl(VendorDictionaryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void addVendor(String tenantId, String vendorName, String source) {
        if (vendorName == null || vendorName.trim().isEmpty()) {
            return;
        }

        String normalized = vendorName.trim();
        if ("Unknown".equalsIgnoreCase(normalized) || normalized.toLowerCase().contains("unknown vendor")) {
            return;
        }
        if (!repository.existsByTenantIdAndVendorName(tenantId, normalized)) {
            VendorDictionary entry = new VendorDictionary();
            entry.setTenantId(tenantId);
            entry.setVendorName(normalized);
            entry.setAddedSource(source);
            repository.save(entry);
            log.info("Added '{}' to Vendor Dictionary from source: {}", normalized, source);
            // Auto-export after adding
            exportDictionaryToJson(tenantId);
        }
    }

    @Override
    public List<String> getVendorNames(String tenantId) {
        return repository.findAllByTenantId(tenantId)
                .stream()
                .map(VendorDictionary::getVendorName)
                .collect(Collectors.toList());
    }

    @Override
    public void exportDictionaryToJson(String tenantId) {
        try {
            List<String> vendors = getVendorNames(tenantId);
            Map<String, Object> data = new HashMap<>();
            data.put("vendors", vendors);
            data.put("tenantId", tenantId);
            data.put("updatedAt", java.time.LocalDateTime.now().toString());

            File file = new File(EXPORT_PATH);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
            log.info("Exported {} vendors to {}", vendors.size(), EXPORT_PATH);
        } catch (Exception e) {
            log.error("Failed to export vendor dictionary: {}", e.getMessage());
        }
    }
}
