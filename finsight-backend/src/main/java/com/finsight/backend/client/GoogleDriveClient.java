package com.finsight.backend.client;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

@Component
public class GoogleDriveClient {

    private static final String APPLICATION_NAME = "FinSight";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    public Drive getDriveService(String serviceAccountJson) throws GeneralSecurityException, IOException {
        final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials = com.finsight.backend.util.GoogleCredentialsResolver
                .resolve(serviceAccountJson, java.util.Collections.singletonList("https://www.googleapis.com/auth/drive"));
        
        return new Drive.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public List<File> listFilesRecursively(Drive service, String folderId) throws IOException {
        List<File> allFiles = new ArrayList<>();
        Queue<String> folderQueue = new LinkedList<>();
        folderQueue.add(folderId);

        while (!folderQueue.isEmpty()) {
            String currentFolderId = folderQueue.poll();
            String query = String.format("'%s' in parents and trashed = false", currentFolderId);
            String pageToken = null;
            do {
                Drive.Files.List request = service.files().list()
                        .setQ(query)
                        .setFields("nextPageToken, files(id, name, mimeType, md5Checksum, webViewLink)")
                        .setPageToken(pageToken);
                FileList result = request.execute();

                if (result.getFiles() != null) {
                    for (File file : result.getFiles()) {
                        if ("application/vnd.google-apps.folder".equals(file.getMimeType())) {
                            folderQueue.add(file.getId());
                        } else if (file.getMimeType().startsWith("image/")
                                || "application/pdf".equals(file.getMimeType())) {
                            allFiles.add(file);
                        }
                    }
                }
                pageToken = result.getNextPageToken();
            } while (pageToken != null);
        }
        return allFiles;
    }

    public byte[] downloadFile(Drive service, String fileId) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Drive.Files.Get getRequest = service.files().get(fileId);
        getRequest.executeMediaAndDownloadTo(outputStream);
        return outputStream.toByteArray();
    }
    public String createFile(Drive service, String name, String mimeType) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType(mimeType);

        File file = service.files().create(fileMetadata)
                .setFields("id")
                .execute();
        return file.getId();
    }

    public void deleteFile(Drive service, String fileId) throws IOException {
        service.files().delete(fileId).execute();
    }
}
