package com.finsight.backend.util;

import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * Universal resolver for Google Service Account credentials.
 * Supports both raw JSON strings and file paths.
 * Automatically heals common JSON string corruption (escaped newlines).
 */
public class GoogleCredentialsResolver {

    private static final Logger log = LoggerFactory.getLogger(GoogleCredentialsResolver.class);

    public static GoogleCredentials resolve(String config, Collection<String> scopes) throws IOException {
        if (config == null || config.trim().isEmpty()) {
            log.info("No Google configuration provided, using Application Default Credentials.");
            return GoogleCredentials.getApplicationDefault().createScoped(scopes);
        }

        String jsonContent;
        String trimmed = config.trim();

        // 1. Path Detection: Check if it looks like a file path
        if (isFilePath(trimmed)) {
            log.info("Google config detected as File Path: {}", trimmed);
            jsonContent = Files.readString(Paths.get(trimmed));
        } else {
            log.info("Google config detected as Raw JSON String.");
            jsonContent = healJson(trimmed);
        }

        return GoogleCredentials.fromStream(new ByteArrayInputStream(jsonContent.getBytes(StandardCharsets.UTF_8)))
                .createScoped(scopes);
    }

    private static boolean isFilePath(String input) {
        // Simple heuristic: paths usually start with /, ./, or a drive letter (C:\) or end with .json
        return input.startsWith("/") || 
               input.startsWith("./") || 
               input.startsWith("..") ||
               (input.length() > 3 && input.charAt(1) == ':' && input.charAt(2) == '\\') || // Windows
               input.toLowerCase().endsWith(".json");
    }

    /**
     * Fixes common corruption in JSON strings when pasted into DB/Env fields.
     * Specifically converts literal "\n" character sequences back to actual newlines.
     */
    private static String healJson(String input) {
        if (input.contains("\\n")) {
            log.debug("Found escaped newlines in JSON; performing healing.");
            // Replace literal \n with actual newline. 
            // We use \\n in the regex to match the slash-n sequence.
            return input.replace("\\n", "\n");
        }
        return input;
    }
}
