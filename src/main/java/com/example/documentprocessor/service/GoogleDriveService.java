package com.example.documentprocessor.service;

import com.example.documentprocessor.model.ProcessingResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleDriveService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.drive.api.base-url:https://www.googleapis.com/drive/v3}")
    private String driveApiBaseUrl;

    @Value("${google.docs.api.base-url:https://docs.googleapis.com/v1}")
    private String docsApiBaseUrl;

    public ProcessingResult downloadFile(String fileId, String accessToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                driveApiBaseUrl + "/files/" + fileId + "?alt=media",
                HttpMethod.GET, requestEntity, byte[].class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String contentType = response.getHeaders().getContentType() != null ?
                    response.getHeaders().getContentType().toString() : "application/octet-stream";

                return ProcessingResult.builder()
                    .success(true)
                    .fileData(response.getBody())
                    .mimeType(contentType)
                    .fileId(fileId)
                    .build();
            } else {
                return ProcessingResult.builder()
                    .success(false)
                    .error("Download failed: " + response.getStatusCode())
                    .stage("File Download")
                    .build();
            }
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
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

            // Metadata
            String metadata = String.format(
                "{\"name\":\"%s\",\"parents\":[\"%s\"]}",
                fileName, parentFolderId
            );
            body.add("metadata", metadata);

            // File data
            body.add("file", new org.springframework.core.io.ByteArrayResource(fileData) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                driveApiBaseUrl + "/files?uploadType=multipart",
                HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String uploadedFileId = responseJson.path("id").asText();

                return ProcessingResult.builder()
                    .success(true)
                    .fileId(uploadedFileId)
                    .exportedFileId(uploadedFileId)
                    .build();
            } else {
                return ProcessingResult.builder()
                    .success(false)
                    .error("Upload failed: " + response.getStatusCode())
                    .stage("File Upload")
                    .build();
            }
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
            // First create empty document
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            String createRequest = String.format(
                "{\"title\":\"%s\",\"parents\":[\"%s\"]}",
                title, parentFolderId
            );

            HttpEntity<String> createEntity = new HttpEntity<>(createRequest, headers);

            ResponseEntity<String> createResponse = restTemplate.exchange(
                docsApiBaseUrl + "/documents",
                HttpMethod.POST, createEntity, String.class);

            if (!createResponse.getStatusCode().is2xxSuccessful()) {
                return ProcessingResult.builder()
                    .success(false)
                    .error("Document creation failed: " + createResponse.getStatusCode())
                    .stage("Document Creation")
                    .build();
            }

            JsonNode createJson = objectMapper.readTree(createResponse.getBody());
            String documentId = createJson.path("documentId").asText();

            // Then update with content if provided
            if (content != null && !content.isEmpty()) {
                String updateRequest = String.format(
                    "{\"requests\":[{\"insertText\":{\"location\":{\"index\":1},\"text\":\"%s\"}}]}",
                    content.replace("\"", "\\\"").replace("\n", "\\n")
                );

                HttpEntity<String> updateEntity = new HttpEntity<>(updateRequest, headers);

                restTemplate.exchange(
                    docsApiBaseUrl + "/documents/" + documentId + ":batchUpdate",
                    HttpMethod.POST, updateEntity, String.class);
            }

            return ProcessingResult.builder()
                .success(true)
                .fileId(documentId)
                .exportedFileId(documentId)
                .build();
        } catch (Exception e) {
            log.error("Error creating document in Google Docs", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("Document Creation")
                .build();
        }
    }
}