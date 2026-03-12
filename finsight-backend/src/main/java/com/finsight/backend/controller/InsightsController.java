package com.finsight.backend.controller;

import com.finsight.backend.dto.AnomalyInsightDto;
import com.finsight.backend.dto.CategoryInsightDto;
import com.finsight.backend.dto.VendorInsightDto;
import com.finsight.backend.entity.ForensicAnomaly;
import com.finsight.backend.repository.ForensicAnomalyRepository;
import com.finsight.backend.service.AnomalyDetectionService;
import com.finsight.backend.service.VendorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/insights")
@CrossOrigin(origins = "*")
public class InsightsController {

    private final VendorService vendorService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final ForensicAnomalyRepository forensicAnomalyRepository;

    public InsightsController(VendorService vendorService, 
                              AnomalyDetectionService anomalyDetectionService,
                              ForensicAnomalyRepository forensicAnomalyRepository) {
        this.vendorService = vendorService;
        this.anomalyDetectionService = anomalyDetectionService;
        this.forensicAnomalyRepository = forensicAnomalyRepository;
    }

    @GetMapping("/vendors/top")
    public ResponseEntity<List<VendorInsightDto>> getTopVendors(@RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(vendorService.getTopVendors(limit));
    }

    @GetMapping("/categories/spend")
    public ResponseEntity<List<CategoryInsightDto>> getSpendByCategory() {
        return ResponseEntity.ok(vendorService.getSpendByCategory());
    }

    @GetMapping("/anomalies/detect")
    public ResponseEntity<?> runAnomalyDetection() {
        try {
            List<AnomalyInsightDto> anomalies = anomalyDetectionService.detectAnomalies();
            Map<String, Object> response = new HashMap<>();
            response.put("anomalies", anomalies);
            response.put("count", anomalies.size());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to run anomaly detection."));
        }
    }

    @GetMapping("/anomalies/history")
    public ResponseEntity<List<ForensicAnomaly>> getAnomalyHistory() {
        return ResponseEntity.ok(forensicAnomalyRepository.findByTenantIdOrderByDetectedAtDesc("local_tenant"));
    }

    @GetMapping("/ocr-stats")
    public ResponseEntity<Map<String, Long>> getOcrStats() {
        return ResponseEntity.ok(vendorService.getOcrModeStats());
    }
}
