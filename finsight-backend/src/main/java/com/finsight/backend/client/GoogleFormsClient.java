package com.finsight.backend.client;

import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.forms.v1.Forms;
import com.google.api.services.forms.v1.FormsScopes;
import com.google.api.services.drive.DriveScopes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

@Component
public class GoogleFormsClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleFormsClient.class);
    private static final String APPLICATION_NAME = "FinSight";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Arrays.asList(
            FormsScopes.FORMS_BODY,
            FormsScopes.FORMS_RESPONSES_READONLY,
            DriveScopes.DRIVE,
            DriveScopes.DRIVE_FILE
    );

    public Forms getFormsService(String serviceAccountJson) throws GeneralSecurityException, IOException {
        final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials;
        
        if (serviceAccountJson != null && !serviceAccountJson.trim().isEmpty()) {
            credentials = GoogleCredentials
                    .fromStream(new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)))
                    .createScoped(SCOPES);
            if (credentials instanceof com.google.auth.oauth2.ServiceAccountCredentials) {
                log.info("Using Service Account Identity: {}", ((com.google.auth.oauth2.ServiceAccountCredentials)credentials).getClientEmail());
            }
        } else {
            // Fallback to application default credentials if no JSON provided
            credentials = GoogleCredentials.getApplicationDefault().createScoped(SCOPES);
        }
        
        return new Forms.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
