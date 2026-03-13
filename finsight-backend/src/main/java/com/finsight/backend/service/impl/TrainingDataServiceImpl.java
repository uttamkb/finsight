package com.finsight.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.service.TrainingDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class TrainingDataServiceImpl implements TrainingDataService {
    private static final Logger log = LoggerFactory.getLogger(TrainingDataServiceImpl.class);
    private static final String HARVEST_DIR = "training_data/harvested";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrainingDataServiceImpl() {
        // Ensure directory exists
        try {
            Files.createDirectories(Paths.get(HARVEST_DIR));
        } catch (Exception e) {
            log.error("Could not create training data directory: {}", e.getMessage());
        }
    }

    @Override
    public void harvest(Receipt receipt, byte[] fileData) {
        String baseName = "sample_" + receipt.getId() + "_" + System.currentTimeMillis();
        try {
            // 1. Save File (Image/PDF)
            String extension = ".img";
            if (receipt.getFileName() != null && receipt.getFileName().toLowerCase().endsWith(".pdf")) {
                extension = ".pdf";
            } else if (receipt.getFileName() != null && receipt.getFileName().contains(".")) {
                extension = receipt.getFileName().substring(receipt.getFileName().lastIndexOf("."));
            }
            
            Path filePath = Paths.get(HARVEST_DIR, baseName + extension);
            Files.write(filePath, fileData);

            // 2. Save JSON Metadata (Ground Truth)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("vendor", receipt.getVendor());
            metadata.put("amount", receipt.getAmount());
            metadata.put("date", receipt.getDate() != null ? receipt.getDate().toString() : null);
            metadata.put("category", receipt.getCategory());
            metadata.put("original_filename", receipt.getFileName());
            metadata.put("drive_file_id", receipt.getDriveFileId());

            Path jsonPath = Paths.get(HARVEST_DIR, baseName + ".json");
            objectMapper.writeValue(jsonPath.toFile(), metadata);

            log.info("Successfully harvested golden sample for receipt {}: {}", receipt.getId(), baseName);
        } catch (Exception e) {
            log.error("Failed to harvest training data for receipt {}: {}", receipt.getId(), e.getMessage());
        }
    }
}
