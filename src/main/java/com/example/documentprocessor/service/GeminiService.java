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

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api.upload-url}")
    private String uploadUrl;

    @Value("${app.gemini.api.generate-url}")
    private String generateUrl;

    @Value("${app.gemini.api.cleanup-url}")
    private String cleanupUrl;

    public ProcessingResult uploadFile(byte[] fileData, String mimeType, String apiKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new org.springframework.core.io.ByteArrayResource(fileData) {
                @Override
                public String getFilename() {
                    return "document." + getExtensionFromMimeType(mimeType);
                }
            });

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                uploadUrl, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String fileUri = responseJson.path("file").path("uri").asText();
                String fileName = responseJson.path("file").path("name").asText();

                return ProcessingResult.builder()
                    .success(true)
                    .fileId(fileName)
                    .build();
            } else {
                return ProcessingResult.builder()
                    .success(false)
                    .error("Upload failed: " + response.getStatusCode())
                    .stage("File Upload")
                    .build();
            }
        } catch (Exception e) {
            log.error("Error uploading file to Gemini", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("File Upload")
                .build();
        }
    }

    public ProcessingResult generateContent(String prompt, String fileUri, String apiKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> fileData = Map.of(
                "mime_type", getMimeTypeFromUri(fileUri),
                "file_uri", fileUri
            );

            Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                    Map.of("parts", new Object[]{
                        Map.of("text", prompt),
                        Map.of("file_data", fileData)
                    })
                }
            );

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                generateUrl, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String text = responseJson.path("candidates")
                    .get(0).path("content").path("parts").get(0).path("text").asText();

                return ProcessingResult.builder()
                    .success(true)
                    .extractedText(text)
                    .build();
            } else {
                return ProcessingResult.builder()
                    .success(false)
                    .error("Generation failed: " + response.getStatusCode())
                    .stage("Content Generation")
                    .build();
            }
        } catch (Exception e) {
            log.error("Error generating content with Gemini", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("Content Generation")
                .build();
        }
    }

    public ProcessingResult cleanupFile(String fileName, String apiKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<Void> response = restTemplate.exchange(
                cleanupUrl + "/" + fileName, HttpMethod.DELETE, requestEntity, Void.class);

            return ProcessingResult.builder()
                .success(response.getStatusCode().is2xxSuccessful())
                .build();
        } catch (Exception e) {
            log.warn("Error cleaning up Gemini file: {}", e.getMessage());
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("File Cleanup")
                .build();
        }
    }

    private String getExtensionFromMimeType(String mimeType) {
        switch (mimeType) {
            case "application/pdf": return "pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return "docx";
            default: return "bin";
        }
    }

    private String getMimeTypeFromUri(String uri) {
        if (uri.contains(".pdf")) return "application/pdf";
        if (uri.contains(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }
}