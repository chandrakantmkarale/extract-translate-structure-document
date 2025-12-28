package com.example.documentprocessor.shared.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents the result of a processing operation.
 * Used to communicate success/failure and data between processing stages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessingResult {

    private boolean success;
    private String error;
    private String stage;
    private String fileId;
    private String bookName;

    // OCR results
    private String extractedText;

    // Translation results
    private Map<String, String> translations;
    private String translatedText;
    private String targetLanguage;

    // Persistence results
    private String persistedFileId;

    // Export results
    private String exportedFileId;

    // Key rotation results
    private String selectedKey;

    // Fetch results
    private byte[] fileData;
    private String mimeType;

    // Structure results
    private Map<String, Object> structuredData;

    // Generic processing results
    private String processedContent;

    // Logging results
    private String logEntry;

    public String getTranslatedText() {
        return translatedText;
    }

    public void setTranslatedText(String translatedText) {
        this.translatedText = translatedText;
    }

    /**
     * Creates a successful processing result.
     *
     * @param stage The processing stage
     * @return A successful ProcessingResult
     */
    public static ProcessingResult success(String stage) {
        return ProcessingResult.builder()
                .success(true)
                .stage(stage)
                .build();
    }

    /**
     * Creates a failed processing result with an error message.
     *
     * @param stage The processing stage
     * @param error The error message
     * @return A failed ProcessingResult
     */
    public static ProcessingResult failure(String stage, String error) {
        return ProcessingResult.builder()
                .success(false)
                .error(error)
                .stage(stage)
                .build();
    }
}