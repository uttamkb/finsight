package com.finsight.backend.controller;

import com.finsight.backend.dto.TrainingSummaryResponse;
import com.finsight.backend.service.ParserTrainingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/parser")
public class ParserTrainingController {

    private static final Logger log = LoggerFactory.getLogger(ParserTrainingController.class);

    private final ParserTrainingService trainingService;

    public ParserTrainingController(ParserTrainingService trainingService) {
        this.trainingService = trainingService;
    }

    /**
     * Bulk Training API for statement parsers.
     * Learns signatures and structural mappings from multiple Excel/CSV files.
     */
    @PostMapping("/train")
    public ResponseEntity<TrainingSummaryResponse> trainBulk(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "tenantId", defaultValue = "DEFAULT") String tenantId) {
        
        log.info("Received {} files for training (tenantId: {})", files.size(), tenantId);
        TrainingSummaryResponse summary = trainingService.trainBulk(tenantId, files);
        return ResponseEntity.ok(summary);
    }
}
