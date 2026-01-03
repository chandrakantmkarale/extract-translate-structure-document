package com.example.documentprocessor.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;

@Configuration
@Slf4j
public class GoogleDriveAuthConfig {

    @Value("${google.service-account.key-file:D:\\n8n-learning\\csvfolder\\n8n-booktranslationworkflow-012fdc6ac4b1.json}")
    private String serviceAccountKeyFilePath;

    @Value("${google.application.name:Document Processor Service}")
    private String applicationName;

    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    @Bean
    public GoogleCredentials googleCredentials() throws IOException {
        log.info("Initializing Google Service Account credentials from: {}", serviceAccountKeyFilePath);

        try (FileInputStream serviceAccountStream = new FileInputStream(serviceAccountKeyFilePath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
                .createScoped(Collections.singleton(DriveScopes.DRIVE));

            log.info("Google Service Account credentials initialized successfully");
            return credentials;
        } catch (IOException e) {
            log.error("Failed to load Google Service Account credentials from: {}", serviceAccountKeyFilePath, e);
            throw e;
        }
    }

    @Bean
    public Drive driveApiService(GoogleCredentials credentials) throws IOException {
        log.info("Creating Google Drive service with service account authentication");

        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, request -> {
            credentials.refreshIfExpired();
            request.getHeaders().setAuthorization("Bearer " + credentials.getAccessToken().getTokenValue());
        })
        .setApplicationName(applicationName)
        .build();
    }
}