package com.finsight.backend.service;

import com.finsight.backend.entity.Receipt;

public interface TrainingDataService {
    /**
     * Harvests the receipt image and its current (verified) data as a training sample.
     * @param receipt The receipt entity with corrected fields.
     * @param fileData The original binary content of the receipt (image or PDF).
     */
    void harvest(Receipt receipt, byte[] fileData);
}
