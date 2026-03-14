package com.finsight.backend.controller;

import com.finsight.backend.service.TrainingDataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/training")
@CrossOrigin(origins = "*")
@Tag(name = "AI Model Training", description = "Endpoints for harvesting receipt dataset samples and fine-tuning the TrOCR engine")
public class TrainingController {

    private final TrainingDataService trainingDataService;
    private final com.finsight.backend.service.VendorDictionaryService vendorDictionaryService;

    public TrainingController(TrainingDataService trainingDataService, com.finsight.backend.service.VendorDictionaryService vendorDictionaryService) {
        this.trainingDataService = trainingDataService;
        this.vendorDictionaryService = vendorDictionaryService;
    }

    @GetMapping("/samples")
    @Operation(summary = "Get Target Samples", description = "Lists training image samples locally saved that can be manually curated prior to training.")
    public ResponseEntity<List<Map<String, Object>>> getSamples() {
        return ResponseEntity.ok(trainingDataService.getHarvestedSamples());
    }

    @PutMapping("/samples/{id}")
    public ResponseEntity<Map<String, String>> updateSample(@PathVariable("id") String id, @RequestBody Map<String, Object> metadata) {
        try {
            trainingDataService.updateSample(id, metadata);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Sample updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @DeleteMapping("/samples/{id}")
    public ResponseEntity<Map<String, String>> deleteSample(@PathVariable("id") String id) {
        try {
            trainingDataService.deleteSample(id);
            return ResponseEntity.ok(Map.of("status", "success", "message", "Sample deleted successfully."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/harvest")
    @Operation(summary = "Harvest Training Data", description = "Manually copies newly scanned receipts to the raw dataset folder based on recent history.")
    public ResponseEntity<Map<String, Object>> harvestManual() {
        try {
            int count = trainingDataService.harvestManual();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Harvested " + count + " new samples.",
                "count", count
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/prepare")
    @Operation(summary = "Prepare Dataset Pipeline", description = "Applies augmentations (blur, resize) and splits harvested data into train/val datasets via python helper.")
    public ResponseEntity<Map<String, String>> prepareDataset() {
        try {
            String output = trainingDataService.prepareDataset();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Dataset prepared successfully.",
                "output", output
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error", "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/train")
    @Operation(summary = "Launch Neural Engine Training", description = "Executes the `finetune_ocr.py` script as a detached operation to train model epochs sequentially.")
    public ResponseEntity<Map<String, String>> trainModel() {
        try {
            String output = trainingDataService.runTraining();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Training initiated successfully.",
                "output", output
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error", "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/deploy")
    public ResponseEntity<Map<String, String>> deployModel() {
        try {
            String output = trainingDataService.deployModel();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Model deployed successfully.",
                "output", output
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "status", "error", "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/dictionary/stats")
    public ResponseEntity<Map<String, Object>> getDictionaryStats() {
        try {
            List<String> vendors = vendorDictionaryService.getVendorNames("local_tenant");
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "count", vendors.size(),
                "vendors", vendors
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
