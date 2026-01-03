package com.example.documentprocessor.service;

import com.example.documentprocessor.model.ProcessingResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.url}")
    private String generateUrl;

    @Value("${gemini.api.model}")
    private String modelName;

    @Value("${app.gemini.api-key}")
    private String geminiApiKey;

    public ProcessingResult performOcr(byte[] fileData, String mimeType, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Encode file data as base64
            String base64Data = Base64.getEncoder().encodeToString(fileData);

            // Create the request body for multimodal content
            Map<String, Object> filePart = Map.of(
                "inline_data", Map.of(
                    "mime_type", mimeType,
                    "data", base64Data
                )
            );

            Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                    Map.of("parts", new Object[]{
                        Map.of("text", prompt),
                        filePart
                    })
                }
            );

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.info("Calling unified Gemini API for OCR with model: {}, mimeType: {}, file size: {} bytes",
                modelName, mimeType, fileData.length);

            String urlWithKey = generateUrl + "?key=" + geminiApiKey;

            ResponseEntity<String> response = restTemplate.exchange(
                urlWithKey, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String extractedText = responseJson.path("candidates")
                    .get(0).path("content").path("parts").get(0).path("text").asText();

                return ProcessingResult.builder()
                    .success(true)
                    .extractedText(extractedText)
                    .stage("OCR")
                    .build();
            } else {
                return ProcessingResult.builder()
                    .success(false)
                    .error("OCR failed: " + response.getStatusCode())
                    .stage("OCR")
                    .build();
            }
        } catch (Exception e) {
            log.error("Error performing OCR with unified Gemini API", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("OCR")
                .build();
        }
    }

    public ProcessingResult performTranslation(String sourceText, String targetLanguage, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Prepare the prompt with source text and target language
            String fullPrompt = prompt.replace("{sourceText}", sourceText)
                .replace("{targetLanguage}", targetLanguage);

            Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                    Map.of("parts", new Object[]{
                        Map.of("text", fullPrompt)
                    })
                }
            );

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.info("Calling unified Gemini API for translation with model: {}, target language: {}",
                modelName, targetLanguage);

            String urlWithKey = generateUrl + "?key=" + geminiApiKey;

            ResponseEntity<String> response = restTemplate.exchange(
                urlWithKey, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String translatedText = responseJson.path("candidates")
                    .get(0).path("content").path("parts").get(0).path("text").asText();

                return ProcessingResult.builder()
                    .success(true)
                    .extractedText(translatedText)
                    .targetLanguage(targetLanguage)
                    .stage("Translation")
                    .build();
            } else {
                return ProcessingResult.builder()
                    .success(false)
                    .error("Translation failed: " + response.getStatusCode())
                    .stage("Translation")
                    .build();
            }
        } catch (Exception e) {
            log.error("Error performing translation with unified Gemini API", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("Translation")
                .build();
        }
    }
}