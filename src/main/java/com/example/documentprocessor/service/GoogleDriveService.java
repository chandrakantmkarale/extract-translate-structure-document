package com.example.documentprocessor.service;

import com.example.documentprocessor.model.ProcessingResult;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveService {

    private final Drive driveService;

    public ProcessingResult downloadFile(String fileId) {
        try {
            // Get file metadata to retrieve mimeType
            File file = driveService.files().get(fileId).setFields("mimeType").execute();
            String mimeType = file.getMimeType();

            // Download file content
            InputStream inputStream = driveService.files().get(fileId).executeMediaAsInputStream();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            byte[] fileData = outputStream.toByteArray();
            inputStream.close();
            outputStream.close();

            return ProcessingResult.builder()
                .success(true)
                .fileData(fileData)
                .mimeType(mimeType)
                .fileId(fileId)
                .build();
        } catch (Exception e) {
            log.error("Error downloading file from Google Drive", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("File Download")
                .build();
        }
    }

    public ProcessingResult uploadFile(byte[] fileData, String fileName, String parentFolderId,
                                     String mimeType) {
        try {
            File fileMetadata = new File()
                .setName(fileName)
                .setParents(Arrays.asList(parentFolderId));

            AbstractInputStreamContent mediaContent = new ByteArrayContent(mimeType, fileData);

            File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

            String uploadedFileId = uploadedFile.getId();

            return ProcessingResult.builder()
                .success(true)
                .fileId(uploadedFileId)
                .exportedFileId(uploadedFileId)
                .build();
        } catch (Exception e) {
            log.error("Error uploading file to Google Drive", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("File Upload")
                .build();
        }
    }

    public ProcessingResult createDocument(String title, String parentFolderId) {
        try {
            File fileMetadata = new File()
                .setName(title)
                .setMimeType("application/vnd.google-apps.document")
                .setParents(Arrays.asList(parentFolderId));

            File createdFile = driveService.files().create(fileMetadata)
                .setFields("id")
                .execute();

            String documentId = createdFile.getId();

            return ProcessingResult.builder()
                .success(true)
                .fileId(documentId)
                .exportedFileId(documentId)
                .build();
        } catch (Exception e) {
            log.error("Error creating document in Google Drive", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("Document Creation")
                .build();
        }
    }
}