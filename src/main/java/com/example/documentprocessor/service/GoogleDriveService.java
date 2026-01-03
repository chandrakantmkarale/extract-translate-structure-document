package com.example.documentprocessor.service;

import com.example.documentprocessor.model.ProcessingResult;
import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Service
@Slf4j
public class GoogleDriveService {

    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

    private Drive getDriveService(String accessToken) {
        return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpRequestInitializer() {
            @Override
            public void initialize(com.google.api.client.http.HttpRequest request) throws IOException {
                request.getHeaders().setAuthorization("Bearer " + accessToken);
            }
        }).setApplicationName("Document Processor").build();
    }

    public ProcessingResult downloadFile(String fileId, String accessToken) {
        try {
            Drive drive = getDriveService(accessToken);

            // Get file metadata to retrieve mimeType
            File file = drive.files().get(fileId).setFields("mimeType").execute();
            String mimeType = file.getMimeType();

            // Download file content
            InputStream inputStream = drive.files().get(fileId).executeMediaAsInputStream();
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
                                     String mimeType, String accessToken) {
        try {
            Drive drive = getDriveService(accessToken);

            File fileMetadata = new File()
                .setName(fileName)
                .setParents(Arrays.asList(parentFolderId));

            AbstractInputStreamContent mediaContent = new ByteArrayContent(mimeType, fileData);

            File uploadedFile = drive.files().create(fileMetadata, mediaContent)
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

    public ProcessingResult createDocument(String title, String content, String parentFolderId, String accessToken) {
        try {
            Drive drive = getDriveService(accessToken);

            File fileMetadata = new File()
                .setName(title)
                .setMimeType("application/vnd.google-apps.document")
                .setParents(Arrays.asList(parentFolderId));

            File createdFile = drive.files().create(fileMetadata)
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