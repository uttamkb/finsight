package com.finsight.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.client.GoogleDriveClient;
import com.finsight.backend.service.AppConfigService;
import com.finsight.backend.service.VendorDictionaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TrainingDataServiceImplTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private GoogleDriveClient driveClient;

    @Mock
    private AppConfigService appConfigService;

    @Mock
    private VendorDictionaryService vendorDictionaryService;

    private TrainingDataServiceImpl trainingDataService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        trainingDataService = new TrainingDataServiceImpl(receiptRepository, driveClient, appConfigService, vendorDictionaryService, "target/training_data/harvested");
    }

    @Test
    void testUpdateSample() throws Exception {
        // Arrange
        String sampleId = "test_sample_123";
        Path harvestPath = Path.of("target/training_data/harvested");
        Files.createDirectories(harvestPath);
        Path jsonPath = harvestPath.resolve(sampleId + ".json");
        
        Map<String, Object> initialMetadata = new HashMap<>();
        initialMetadata.put("vendor", "Old Vendor");
        initialMetadata.put("amount", 100.0);
        Files.writeString(jsonPath, objectMapper.writeValueAsString(initialMetadata));

        Map<String, Object> updates = new HashMap<>();
        updates.put("vendor", "New Vendor");
        updates.put("verified", true);

        // Act
        trainingDataService.updateSample(sampleId, updates);

        // Assert
        String updatedJson = Files.readString(jsonPath);
        Map<String, Object> result = objectMapper.readValue(updatedJson, Map.class);
        assertEquals("New Vendor", result.get("vendor"));
        assertEquals(true, result.get("verified"));
        
        // Verify VendorDictionaryService was called
        verify(vendorDictionaryService).addVendor(any(), eq("New Vendor"), eq("MANUAL_VERIFICATION"));
        
        // Cleanup
        Files.deleteIfExists(jsonPath);
    }

    @Test
    void testDeleteSample() throws Exception {
        // Arrange
        String sampleId = "to_delete";
        Path harvestPath = Path.of("target/training_data/harvested");
        Files.createDirectories(harvestPath);
        Path jsonPath = harvestPath.resolve(sampleId + ".json");
        Path imgPath = harvestPath.resolve(sampleId + ".jpg");
        
        Files.writeString(jsonPath, "{}");
        Files.writeString(imgPath, "fake-image-data");

        // Act
        trainingDataService.deleteSample(sampleId);

        // Assert
        assertFalse(Files.exists(jsonPath));
        assertFalse(Files.exists(imgPath));
    }
}
