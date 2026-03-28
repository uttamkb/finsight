package com.finsight.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

/**
 * Centralized Runtime Error Logger.
 * Writes structured JSON Lines to /agent/runtime-errors.jsonl
 * Append-only — never overwrites existing data.
 */
public class RuntimeErrorLogger {

    private static final Logger log = LoggerFactory.getLogger(RuntimeErrorLogger.class);
    private static final String LOG_FILE = "agent/runtime-errors.jsonl";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public enum Module {
        PARSER, OCR, RECONCILIATION, GEMINI, STATEMENT_UPLOAD, GENERAL
    }

    /**
     * Log a runtime error with full context.
     *
     * @param module      The module where the error occurred.
     * @param errorType   Short label, e.g. "DateParseFailure", "NullPointerException"
     * @param throwable   The exception (can be null if validation-only)
     * @param inputData   Raw input that caused the error (file name, cell value, etc.)
     * @param expected    What was expected (can be null)
     * @param actual      What was actually observed (can be null)
     */
    public static void log(Module module, String errorType, Throwable throwable,
                           Map<String, Object> inputData, String expected, String actual) {
        try {
            ensureLogDirectoryExists();

            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("timestamp", Instant.now().toString());
            entry.put("module", module.name());
            entry.put("errorType", errorType);

            // Stack trace
            if (throwable != null) {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                entry.put("message", throwable.getMessage());
                entry.put("stackTrace", sw.toString().substring(0, Math.min(sw.toString().length(), 2000)));
            }

            // Input data (key-value pairs from caller)
            if (inputData != null && !inputData.isEmpty()) {
                ObjectNode inputNode = MAPPER.createObjectNode();
                inputData.forEach((k, v) -> inputNode.put(k, v != null ? v.toString() : "null"));
                entry.set("inputData", inputNode);
            }

            // Expected vs Actual
            if (expected != null) entry.put("expected", expected);
            if (actual != null) entry.put("actual", actual);

            // Append to file
            try (FileWriter fw = new FileWriter(LOG_FILE, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(MAPPER.writeValueAsString(entry));
            }

        } catch (Exception e) {
            log.error("RuntimeErrorLogger failed to write: {}", e.getMessage());
        }
    }

    /** Convenience overload — exception only, no input data. */
    public static void log(Module module, String errorType, Throwable throwable) {
        log(module, errorType, throwable, null, null, null);
    }

    /** Convenience overload — validation failure with no throwable. */
    public static void logValidation(Module module, String errorType,
                                     Map<String, Object> inputData, String expected, String actual) {
        log(module, errorType, null, inputData, expected, actual);
    }

    private static void ensureLogDirectoryExists() throws Exception {
        Path dirPath = Paths.get("agent");
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
    }
}
