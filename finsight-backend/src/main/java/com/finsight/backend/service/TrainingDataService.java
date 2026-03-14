package com.finsight.backend.service;

import com.finsight.backend.entity.Receipt;

public interface TrainingDataService {
    /**
     * Harvests the receipt image and its current (verified) data as a training sample.
     * @param receipt The receipt entity with corrected fields.
     * @param fileData The original binary content of the receipt (image or PDF).
     */
    void harvest(Receipt receipt, byte[] fileData);

    /**
     * Lists all harvested golden samples currently in the local workspace.
     */
    java.util.List<java.util.Map<String, Object>> getHarvestedSamples();

    /**
     * Triggers the local Python script to convert harvested samples into a PaddleOCR dataset.
     */
    String prepareDataset() throws Exception;

    /**
     * Updates the ground truth metadata for a specific harvested sample.
     */
    void updateSample(String sampleId, java.util.Map<String, Object> updatedMetadata) throws Exception;

    /**
     * Deletes a harvested sample (both JSON and image/pdf).
     */
    void deleteSample(String sampleId) throws Exception;

    /**
     * Manually triggers harvesting of receipts that are PROCESSED but not yet in the training set.
     */
    int harvestManual();

    /**
     * Triggers the local Python script to perform fine-tuning on the prepared dataset.
     */
    String runTraining() throws Exception;

    /**
     * Deploys the trained model weights to the application's active OCR model directory.
     */
    String deployModel() throws Exception;
}
