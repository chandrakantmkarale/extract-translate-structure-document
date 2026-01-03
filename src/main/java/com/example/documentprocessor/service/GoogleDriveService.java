package com.example.documentprocessor.service;

import com.example.documentprocessor.model.ProcessingResult;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

@Service
@Slf4j
public class GoogleDriveService {

    @Autowired
    private Drive driveApiService;

    public ProcessingResult downloadFile(String fileId) {
        if (driveApiService == null) {
            log.warn("Google Drive service not available - credentials not configured");
            return ProcessingResult.builder()
                .success(false)
                .error("Google Drive service not available - credentials not configured")
                .stage("File Download")
                .build();
        }

        try {
            log.info("Downloading file from Google Drive: {}", fileId);

            // Get file metadata to retrieve mimeType and name
            File fileMetadata = driveApiService.files().get(fileId)
                .setFields("mimeType,name,size")
                .execute();

            String mimeType = fileMetadata.getMimeType();
            String fileName = fileMetadata.getName();
            Long fileSize = fileMetadata.getSize();

            log.info("File metadata - Name: {}, MIME Type: {}, Size: {} bytes", fileName, mimeType, fileSize);

            // Download file content
            try (InputStream inputStream = driveApiService.files().get(fileId).executeMediaAsInputStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192]; // 8KB buffer
                int bytesRead;
                long totalBytesRead = 0;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }

                byte[] fileData = outputStream.toByteArray();

                log.info("Successfully downloaded file: {} ({} bytes)", fileName, totalBytesRead);

                return ProcessingResult.builder()
                    .success(true)
                    .fileData(fileData)
                    .mimeType(mimeType)
                    .fileId(fileId)
                    .fileName(fileName)
                    .build();
            }

        } catch (IOException e) {
            log.error("Error downloading file from Google Drive: {}", fileId, e);
            return ProcessingResult.builder()
                .success(false)
                .error("Failed to download file: " + e.getMessage())
                .stage("File Download")
                .fileId(fileId)
                .build();
        } catch (Exception e) {
            log.error("Unexpected error downloading file from Google Drive: {}", fileId, e);
            return ProcessingResult.builder()
                .success(false)
                .error("Unexpected error: " + e.getMessage())
                .stage("File Download")
                .fileId(fileId)
                .build();
        }
    }

    public ProcessingResult uploadFile(byte[] fileData, String fileName, String parentFolderId, String mimeType) {
        if (driveApiService == null) {
            log.warn("Google Drive service not available - credentials not configured");
            return ProcessingResult.builder()
                .success(false)
                .error("Google Drive service not available - credentials not configured")
                .stage("File Upload")
                .build();
        }

        try {
            log.info("Uploading file to Google Drive: {} ({} bytes)", fileName, fileData.length);

            // Create file metadata
            File fileMetadata = new File()
                .setName(fileName)
                .setParents(Collections.singletonList(parentFolderId));

            // Create content stream
            com.google.api.client.http.ByteArrayContent mediaContent =
                new com.google.api.client.http.ByteArrayContent(mimeType, fileData);

            // Upload file
            File uploadedFile = driveApiService.files().create(fileMetadata, mediaContent)
                .setFields("id,name,mimeType,size,parents")
                .execute();

            String uploadedFileId = uploadedFile.getId();
            String uploadedFileName = uploadedFile.getName();

            log.info("Successfully uploaded file: {} with ID: {}", uploadedFileName, uploadedFileId);

            return ProcessingResult.builder()
                .success(true)
                .fileId(uploadedFileId)
                .fileName(uploadedFileName)
                .mimeType(mimeType)
                .build();

        } catch (IOException e) {
            log.error("Error uploading file to Google Drive: {}", fileName, e);
            return ProcessingResult.builder()
                .success(false)
                .error("Failed to upload file: " + e.getMessage())
                .stage("File Upload")
                .build();
        } catch (Exception e) {
            log.error("Unexpected error uploading file to Google Drive: {}", fileName, e);
            return ProcessingResult.builder()
                .success(false)
                .error("Unexpected error: " + e.getMessage())
                .stage("File Upload")
                .build();
        }
    }

    public ProcessingResult createDocument(String title, String parentFolderId) {
        if (driveApiService == null) {
            log.warn("Google Drive service not available - credentials not configured");
            return ProcessingResult.builder()
                .success(false)
                .error("Google Drive service not available - credentials not configured")
                .stage("Document Creation")
                .build();
        }

        try {
            log.info("Creating Google Docs document: {}", title);

            // Create document metadata
            File fileMetadata = new File()
                .setName(title)
                .setMimeType("application/vnd.google-apps.document")
                .setParents(Collections.singletonList(parentFolderId));

            // Create the document
            File createdFile = driveApiService.files().create(fileMetadata)
                .setFields("id,name,mimeType,parents")
                .execute();

            String documentId = createdFile.getId();
            String documentName = createdFile.getName();

            log.info("Successfully created document: {} with ID: {}", documentName, documentId);

            return ProcessingResult.builder()
                .success(true)
                .fileId(documentId)
                .fileName(documentName)
                .mimeType("application/vnd.google-apps.document")
                .build();

        } catch (IOException e) {
            log.error("Error creating document in Google Drive: {}", title, e);
            return ProcessingResult.builder()
                .success(false)
                .error("Failed to create document: " + e.getMessage())
                .stage("Document Creation")
                .build();
        } catch (Exception e) {
            log.error("Unexpected error creating document in Google Drive: {}", title, e);
            return ProcessingResult.builder()
                .success(false)
                .error("Unexpected error: " + e.getMessage())
                .stage("Document Creation")
                .build();
        }
    }
}