package com.example.documentprocessor.documentprocessing.ocr;

import com.example.documentprocessor.documentprocessing.config.ApplicationProperties;
import com.example.documentprocessor.documentprocessing.keyrotation.KeyRotationService;
import com.example.documentprocessor.shared.model.DocumentRecord;
import com.example.documentprocessor.shared.model.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Service for processing documents using Google's Gemini Vision API.
 * Handles OCR (Optical Character Recognition) operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiProcessor {

    private final ApplicationProperties properties;
    private final KeyRotationService keyRotationService;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    /**
     * Processes a document using Gemini Vision API for OCR.
     *
     * @param exchange The Camel exchange containing the document data
     */
    public void processDocument(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        String operation = exchange.getIn().getHeader("operation", String.class);
        String prompt = exchange.getIn().getHeader("ocrPrompt", String.class);
        byte[] fileData = exchange.getIn().getHeader("fileData", byte[].class);
        String mimeType = exchange.getIn().getHeader("mimeType", String.class);

        try {
            ProcessingResult result = performOcr(record, prompt, fileData, mimeType);
            if (result.isSuccess()) {
                record.setExtractedText(result.getExtractedText());
            } else {
                record.addError("OCR Processing", result.getError());
            }
        } catch (Exception e) {
            log.error("Error during OCR processing for file {}: {}", record.getFileId(), e.getMessage(), e);
            record.addError("OCR Processing", "OCR processing failed: " + e.getMessage());
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Performs OCR using Gemini Vision API.
     */
    private ProcessingResult performOcr(DocumentRecord record, String prompt, byte[] fileData, String mimeType) {
        String apiKey = keyRotationService.getNextKey();

        if (apiKey == null) {
            return ProcessingResult.failure("OCR Processing", "No API keys available");
        }

        try {
            String requestBody = buildGeminiRequest(prompt, fileData, mimeType);
            String response = callGeminiApi(apiKey, requestBody);

            String extractedText = parseGeminiResponse(response);
            if (extractedText == null || extractedText.trim().isEmpty()) {
                return ProcessingResult.failure("OCR Processing", "No text extracted from document");
            }

            return ProcessingResult.builder()
                .success(true)
                .extractedText(extractedText)
                .stage("OCR Processing")
                .build();

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            return ProcessingResult.failure("OCR Processing",
                "Gemini API error: " + e.getMessage());
        }
    }

    /**
     * Builds the JSON request body for Gemini Vision API.
     */
    private String buildGeminiRequest(String prompt, byte[] fileData, String mimeType) {
        String base64Data = Base64.getEncoder().encodeToString(fileData);

        return String.format("""
            {
                "contents": [{
                    "parts": [
                        {"text": "%s"},
                        {
                            "inline_data": {
                                "mime_type": "%s",
                                "data": "%s"
                            }
                        }
                    ]
                }]
            }
            """, escapeJson(prompt), mimeType, base64Data);
    }

    /**
     * Calls the Gemini Vision API.
     */
    private String callGeminiApi(String apiKey, String requestBody) throws Exception {
        String url = properties.getGemini().getVisionApiUrl() + "?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API returned status " + response.statusCode() +
                ": " + response.body());
        }

        return response.body();
    }

    /**
     * Parses the response from Gemini Vision API to extract text.
     */
    private String parseGeminiResponse(String response) {
        // Simple JSON parsing - in production, use a proper JSON library
        try {
            // Look for the text content in the response
            int textStart = response.indexOf("\"text\": \"");
            if (textStart == -1) {
                return null;
            }

            textStart += 9; // Length of "\"text\": \""
            int textEnd = response.indexOf("\"", textStart);
            if (textEnd == -1) {
                return null;
            }

            String extractedText = response.substring(textStart, textEnd);
            // Unescape JSON string
            return extractedText.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Escapes special characters in JSON strings.
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}