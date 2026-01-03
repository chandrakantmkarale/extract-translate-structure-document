package com.example.documentprocessor.service;

import com.example.documentprocessor.model.DocumentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class StructureProcessingService {

    public void processStructure(DocumentRecord record, String sessionId) {
        try {
            log.info("Session {} - Starting structure processing for file: {}", sessionId, record.getFileId());

            // Basic structure processing - organize the extracted data
            if (record.getExtractedText() != null) {
                Map<String, Object> structuredData = new HashMap<>();
                structuredData.put("originalText", record.getExtractedText());
                structuredData.put("textLength", record.getExtractedText().length());
                structuredData.put("hasTranslations", record.getTranslations() != null && !record.getTranslations().isEmpty());

                if (record.getTranslations() != null) {
                    structuredData.put("translationCount", record.getTranslations().size());
                    structuredData.put("targetLanguages", record.getTranslations().keySet());
                }

                record.setStructuredData(structuredData);
                log.info("Session {} - Structure processing completed successfully", sessionId);
            } else {
                log.warn("Session {} - No extracted text to structure", sessionId);
                record.addError("Structure", "No extracted text available");
                record.setAllStagesSuccess(false);
            }

        } catch (Exception e) {
            log.error("Session {} - Error during structure processing for file: {}", sessionId, record.getFileId(), e);
            record.addError("Structure", e.getMessage());
            record.setAllStagesSuccess(false);
        }
    }
}