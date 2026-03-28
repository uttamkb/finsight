package com.finsight.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.service.TrainingDataService;
import com.finsight.backend.client.GoogleDriveClient;
import com.finsight.backend.service.AppConfigService;
import com.finsight.backend.service.VendorDictionaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class TrainingDataServiceImpl implements TrainingDataService {
    private static final Logger log = LoggerFactory.getLogger(TrainingDataServiceImpl.class);
    private final String harvestDir;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReceiptRepository receiptRepository;
    private final GoogleDriveClient driveClient;
    private final AppConfigService appConfigService;
    private final VendorDictionaryService vendorDictionaryService;

    @org.springframework.beans.factory.annotation.Autowired
    public TrainingDataServiceImpl(ReceiptRepository receiptRepository,
                                   GoogleDriveClient driveClient,
                                   AppConfigService appConfigService,
                                   VendorDictionaryService vendorDictionaryService) {
        this(receiptRepository, driveClient, appConfigService, vendorDictionaryService, "training_data/harvested");
    }

    public TrainingDataServiceImpl(ReceiptRepository receiptRepository,
                                   GoogleDriveClient driveClient,
                                   AppConfigService appConfigService,
                                   VendorDictionaryService vendorDictionaryService,
                                   String harvestDir) {
        this.receiptRepository = receiptRepository;
        this.driveClient = driveClient;
        this.appConfigService = appConfigService;
        this.vendorDictionaryService = vendorDictionaryService;
        this.harvestDir = harvestDir;
        // Ensure directory exists
        try {
            Files.createDirectories(Paths.get(harvestDir));
        } catch (Exception e) {
            log.error("Could not create training data directory: {}", e.getMessage());
        }
    }

    @Override
    public void harvest(Receipt receipt, byte[] fileData) {
        try {
            String baseName = "sample_" + receipt.getId();
            saveSample(receipt, fileData, baseName);
            log.info("Successfully harvested golden sample for receipt {}: {}", receipt.getId(), baseName);
        } catch (Exception e) {
            log.error("Failed to harvest training data for receipt {}: {}", receipt.getId(), e.getMessage());
        }
    }

    @Override
    public java.util.List<java.util.Map<String, Object>> getHarvestedSamples() {
        java.util.List<java.util.Map<String, Object>> samples = new java.util.ArrayList<>();
        try {
            java.nio.file.Path harvestPath = java.nio.file.Paths.get(harvestDir);
            if (!java.nio.file.Files.exists(harvestPath)) return samples;

            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(harvestPath)) {
                stream.filter(path -> path.toString().endsWith(".json"))
                      .forEach(jsonPath -> {
                          try {
                              java.util.Map<String, Object> metadata = objectMapper.readValue(jsonPath.toFile(), java.util.Map.class);
                              metadata.put("sampleId", jsonPath.getFileName().toString().replace(".json", ""));
                              samples.add(metadata);
                          } catch (Exception e) {
                              log.error("Failed to read metadata file {}: {}", jsonPath, e.getMessage());
                          }
                      });
            }
        } catch (Exception e) {
            log.error("Failed to list harvested samples: {}", e.getMessage());
        }
        return samples;
    }

    @Override
    public void updateSample(String sampleId, java.util.Map<String, Object> updatedMetadata) throws Exception {
        Path jsonPath = Paths.get(harvestDir, sampleId + ".json");
        if (!Files.exists(jsonPath)) {
            throw new RuntimeException("Sample metadata not found: " + sampleId);
        }
        
        // Read existing, merge updates, and set verified=true
        java.util.Map<String, Object> existing = objectMapper.readValue(jsonPath.toFile(), java.util.Map.class);
        existing.putAll(updatedMetadata);
        existing.put("verified", true);
        
        objectMapper.writeValue(jsonPath.toFile(), existing);
        log.info("Successfully updated and verified sample: {}", sampleId);

        // Add to Vendor Dictionary after human verification
        String vendor = (String) existing.get("vendor");
        if (vendor != null && !"Unknown Vendor".equalsIgnoreCase(vendor)) {
            vendorDictionaryService.addVendor("local_tenant", vendor, "MANUAL_VERIFICATION");
        }
    }

    @Override
    public void deleteSample(String sampleId) throws Exception {
        Path jsonPath = Paths.get(harvestDir, sampleId + ".json");
        if (Files.exists(jsonPath)) {
            // Try to find and delete the associated image/pdf file
            java.util.Map<String, Object> metadata = objectMapper.readValue(jsonPath.toFile(), java.util.Map.class);
            String originalName = (String) metadata.get("original_filename");
            
            // Files are named baseName + extension. sampleId IS the baseName.
            try (java.util.stream.Stream<Path> stream = Files.list(Paths.get(harvestDir))) {
                stream.filter(p -> p.getFileName().toString().startsWith(sampleId) && !p.getFileName().toString().endsWith(".json"))
                      .forEach(p -> {
                          try { Files.delete(p); } catch (Exception e) { log.warn("Failed to delete sample file {}: {}", p, e.getMessage()); }
                      });
            }
            
            Files.delete(jsonPath);
            log.info("Successfully deleted sample: {}", sampleId);
        }
    }

    @Override
    public int harvestManual() {
        log.info("Starting manual harvest scan...");
        int count = 0;
        try {
            // Fix: Use findByStatus instead of findAllByStatus
            java.util.List<Receipt> receipts = receiptRepository.findByStatus("PROCESSED");
            String serviceAccountJson = appConfigService.getConfig().getServiceAccountJson();
            com.google.api.services.drive.Drive driveService = driveClient.getDriveService(serviceAccountJson);

            for (Receipt receipt : receipts) {
                // Use stable ID-based name
                String baseName = "sample_" + receipt.getId();
                Path jsonPath = Paths.get(harvestDir, baseName + ".json");
                
                if (!Files.exists(jsonPath)) {
                    log.info("Harvesting missing sample for receipt ID: {}", receipt.getId());
                    try {
                        byte[] content = driveClient.downloadFile(driveService, receipt.getDriveFileId());
                        // Helper to save specifically with baseName
                        saveSample(receipt, content, baseName);
                        count++;
                    } catch (Exception e) {
                        log.warn("Failed to download/harvest receipt {}: {}", receipt.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Manual harvest failed: {}", e.getMessage());
        }
        return count;
    }

    private void saveSample(Receipt receipt, byte[] fileData, String baseName) throws Exception {
        String extension = ".img";
        if (receipt.getFileName() != null && receipt.getFileName().toLowerCase().endsWith(".pdf")) {
            extension = ".pdf";
        } else if (receipt.getFileName() != null && receipt.getFileName().contains(".")) {
            extension = receipt.getFileName().substring(receipt.getFileName().lastIndexOf("."));
        }
        
        Path filePath = Paths.get(harvestDir, baseName + extension);
        Files.write(filePath, fileData);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("vendor", receipt.getVendor());
        metadata.put("amount", receipt.getAmount());
        metadata.put("date", receipt.getDate() != null ? receipt.getDate().toString() : null);
        metadata.put("category", receipt.getCategory());
        metadata.put("original_filename", receipt.getFileName());
        metadata.put("drive_file_id", receipt.getDriveFileId());
        metadata.put("verified", false);

        Path jsonPath = Paths.get(harvestDir, baseName + ".json");
        objectMapper.writeValue(jsonPath.toFile(), metadata);
    }

    @Override
    public String runTraining() throws Exception {
        log.info("Triggering OCR Model Fine-tuning...");
        String scriptPath = "src/main/resources/scripts/finetune_ocr.py";
        String pythonPath = "src/main/resources/scripts/venv/bin/python3";
        
        return executeScript(pythonPath, scriptPath);
    }

    @Override
    public String deployModel() throws Exception {
        log.info("Deploying fine-tuned OCR model...");
        Path sourcePath = Paths.get("output/finetuned_ocr");
        Path destPath = Paths.get("src/main/resources/models/custom_ocr");
        
        if (!Files.exists(sourcePath)) {
            throw new RuntimeException("No fine-tuned model found in output/finetuned_ocr. Run training first.");
        }
        
        Files.createDirectories(destPath);
        
        // Simulating deployment by copying weight files if they exist
        try (java.util.stream.Stream<Path> stream = Files.list(sourcePath)) {
            stream.forEach(file -> {
                try {
                    Files.copy(file, destPath.resolve(file.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    log.error("Failed to copy model file {}: {}", file, e.getMessage());
                }
            });
        }
        
        return "Model successfully deployed to " + destPath.toAbsolutePath();
    }

    private String executeScript(String pythonPath, String scriptPath) throws Exception {
        File scriptFile = new File(scriptPath);
        if (!scriptFile.exists()) {
            throw new RuntimeException("Script missing: " + scriptPath);
        }

        ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Script failed with exit code " + exitCode + ". Output: " + output.toString());
        }

        return output.toString();
    }

    @Override
    public String prepareDataset() throws Exception {
        log.info("Triggering OCR Dataset Preparation...");
        String scriptPath = "src/main/resources/scripts/prepare_training_data.py";
        String pythonPath = "src/main/resources/scripts/venv/bin/python3";
        return executeScript(pythonPath, scriptPath);
    }

    @Override
    @org.springframework.scheduling.annotation.Async
    public void harvestAsync(Receipt receipt) {
        try {
            var config = appConfigService.getConfig();
            var driveService = driveClient.getDriveService(config.getServiceAccountJson());
            byte[] content = driveClient.downloadFile(driveService, receipt.getDriveFileId());
            harvest(receipt, content);
        } catch (Exception e) {
            log.error("Async harvest failed for receipt {}: {}", receipt.getId(), e.getMessage());
        }
    }
}
