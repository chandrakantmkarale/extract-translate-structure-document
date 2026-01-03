package com.example.documentprocessor.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

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
    private String fileName;
    private String bookName;

    // OCR results
    private String extractedText;

    // Translation results
    private Map<String, String> translations;
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

    // Logging results
    private String logEntry;
}