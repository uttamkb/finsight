package com.finsight.backend.service;

import java.io.InputStream;
import java.util.Map;

public interface OcrService {
    /**
     * Processes an image/PDF and extracts receipt data.
     * @param content The file content
     * @param fileName The file name
     * @param mode The OCR mode (LOW_COST, HYBRID, HIGH_ACCURACY)
     * @return A map containing vendor, amount, date, and confidence
     */
    Map<String, Object> extractData(InputStream content, String fileName, String mode);
}
