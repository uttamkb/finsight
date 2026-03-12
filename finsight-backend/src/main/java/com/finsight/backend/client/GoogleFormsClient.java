package com.finsight.backend.client;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.forms.v1.Forms;
import com.google.api.services.forms.v1.FormsScopes;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

@Component
public class GoogleFormsClient {

    private static final String APPLICATION_NAME = "FinSight";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Arrays.asList(
            FormsScopes.FORMS_BODY,
            FormsScopes.FORMS_RESPONSES_READONLY
    );

    public Forms getFormsService(String serviceAccountJson) throws GeneralSecurityException, IOException {
        final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = null;
        if (serviceAccountJson != null && !serviceAccountJson.trim().isEmpty()) {
            credential = com.google.api.client.googleapis.auth.oauth2.GoogleCredential
                    .fromStream(new java.io.ByteArrayInputStream(serviceAccountJson.getBytes()))
                    .createScoped(SCOPES);
        }
        return new Forms.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
