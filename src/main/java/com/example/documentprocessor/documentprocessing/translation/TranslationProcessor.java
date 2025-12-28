package com.example.documentprocessor.documentprocessing.translation;

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

/**
 * Service for translating text using Google's Gemini API.
 * Handles translation operations for extracted document text.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationProcessor {

    private final ApplicationProperties properties;
    private final KeyRotationService keyRotationService;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    /**
     * Translates text using Gemini API.
     *
     * @param exchange The Camel exchange containing the document data
     */
    public void translateText(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        String operation = exchange.getIn().getHeader("operation", String.class);
        String prompt = exchange.getIn().getHeader("translationPrompt", String.class);
        String extractedText = record.getExtractedText();

        if (extractedText == null || extractedText.trim().isEmpty()) {
            record.addError("Translation Processing", "No extracted text available for translation");
            exchange.getIn().setBody(record);
            return;
        }

        try {
            ProcessingResult result = performTranslation(record, prompt, extractedText);
            if (result.isSuccess()) {
                record.setTranslatedText(result.getTranslatedText());
            } else {
                record.addError("Translation Processing", result.getError());
            }
        } catch (Exception e) {
            log.error("Error during translation processing for file {}: {}",
                record.getFileId(), e.getMessage(), e);
            record.addError("Translation Processing", "Translation failed: " + e.getMessage());
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Performs translation using Gemini API.
     */
    private ProcessingResult performTranslation(DocumentRecord record, String prompt, String text) {
        String apiKey = keyRotationService.getNextKey();

        if (apiKey == null) {
            return ProcessingResult.failure("Translation Processing", "No API keys available");
        }

        try {
            String requestBody = buildGeminiRequest(prompt, text);
            String response = callGeminiApi(apiKey, requestBody);

            String translatedText = parseGeminiResponse(response);
            if (translatedText == null || translatedText.trim().isEmpty()) {
                return ProcessingResult.failure("Translation Processing", "No translation received");
            }

            return ProcessingResult.builder()
                .success(true)
                .translatedText(translatedText)
                .stage("Translation Processing")
                .build();

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            return ProcessingResult.failure("Translation Processing",
                "Gemini API error: " + e.getMessage());
        }
    }

    /**
     * Builds the JSON request body for Gemini API.
     */
    private String buildGeminiRequest(String prompt, String text) {
        String fullPrompt = prompt + "\n\nText to translate:\n" + text;

        return String.format("""
            {
                "contents": [{
                    "parts": [
                        {"text": "%s"}
                    ]
                }]
            }
            """, escapeJson(fullPrompt));
    }

    /**
     * Calls the Gemini API.
     */
    private String callGeminiApi(String apiKey, String requestBody) throws Exception {
        String url = properties.getGemini().getApiUrl() + "?key=" + apiKey;

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
     * Parses the response from Gemini API to extract translated text.
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

            String translatedText = response.substring(textStart, textEnd);
            // Unescape JSON string
            return translatedText.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");

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