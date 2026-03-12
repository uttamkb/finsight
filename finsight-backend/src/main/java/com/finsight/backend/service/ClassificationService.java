package com.finsight.backend.service;

public interface ClassificationService {
    /**
     * Categorizes a receipt or transaction based on its description and vendor.
     * @param description Raw text or description of the transaction.
     * @param vendor Predicted vendor name.
     * @return The most likely category name.
     */
    String classify(String description, String vendor);
}
