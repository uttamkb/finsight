package com.finsight.backend.service.impl;

import com.finsight.backend.entity.Receipt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainingDataServiceImplTest {

    private TrainingDataServiceImpl trainingDataService;
    private static final String TEST_DIR = "training_data/harvested";

    @BeforeEach
    void setUp() {
        trainingDataService = new TrainingDataServiceImpl();
    }

    @Test
    void testHarvest_createsImageAndJsonFiles() {
        Receipt receipt = new Receipt();
        receipt.setId(101L);
        receipt.setVendor("Acme Corp");
        receipt.setAmount(123.45);
        receipt.setFileName("receipt.jpg");

        byte[] fakeImage = "fake_image_content".getBytes();

        trainingDataService.harvest(receipt, fakeImage);

        // Verify files exist in TEST_DIR
        File dir = new File(TEST_DIR);
        assertTrue(dir.exists());

        File[] files = dir.listFiles();
        assertTrue(files != null && files.length >= 2, "Should create exactly two files (img and json)");

        boolean foundJson = false;
        boolean foundJpg = false;
        for (File f : files) {
            if (f.getName().endsWith(".json")) foundJson = true;
            if (f.getName().endsWith(".jpg")) foundJpg = true;
        }

        assertTrue(foundJson, "JSON metadata file not created");
        assertTrue(foundJpg, "Image file not created");
    }
}
