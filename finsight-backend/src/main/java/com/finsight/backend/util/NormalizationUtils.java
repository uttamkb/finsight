package com.finsight.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class NormalizationUtils {

    /**
     * Aggressive header normalization: trim, lowercase, remove non-alphanumeric.
     */
    public static String normalizeHeader(String header) {
        if (header == null) return "";
        return header.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Standard answer normalization: trim, lowercase.
     */
    public static String normalizeAnswer(String answer) {
        if (answer == null) return "";
        return answer.trim().toLowerCase();
    }

    /**
     * Generates a SHA-256 hash from a string.
     */
    public static String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withLowerCase().formatHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
