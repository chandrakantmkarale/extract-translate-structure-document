package com.example.documentprocessor.service;

import com.example.documentprocessor.model.DocumentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
@Slf4j
public class PersistenceProcessingService {

    private final Optional<GoogleDriveService> googleDriveService;

    @Value("${app.output-folder-id:}")
    private String outputFolderId; // This should be configured

    public PersistenceProcessingService(Optional<GoogleDriveService> googleDriveService) {
        this.googleDriveService = googleDriveService;
    }

    public void processPersistence(DocumentRecord record, String sessionId) {
        if (googleDriveService.isEmpty()) {
            log.error("Session {} - Google Drive service not available", sessionId);
            record.addError("Persistence", "Google Drive service not available");
            record.setAllStagesSuccess(false);
            return;
        }

        try {
            log.info("Session {} - Starting persistence processing for file: {}", sessionId, record.getFileId());

            if (record.getTranslations() == null || record.getTranslations().isEmpty()) {
                log.warn("Session {} - No translations to persist", sessionId);
                record.addError("Persistence", "No translations available");
                record.setAllStagesSuccess(false);
                return;
            }

            // Create a document for each translation
            for (var entry : record.getTranslations().entrySet()) {
                String language = entry.getKey();
                String translatedText = entry.getValue();

                String documentTitle = record.getBookName() + " - " + language + " Translation";

                var createResult = googleDriveService.get().createDocument(documentTitle, outputFolderId);

                if (createResult.isSuccess()) {
                    record.setPersistedFileId(createResult.getFileId());
                    log.info("Session {} - Document created successfully for language: {}", sessionId, language);
                } else {
                    log.error("Session {} - Failed to create document for language {}: {}", sessionId, language, createResult.getError());
                    record.addError("Persistence", "Failed to save translation for " + language + ": " + createResult.getError());
                    record.setAllStagesSuccess(false);
                }
            }

        } catch (Exception e) {
            log.error("Session {} - Error during persistence processing for file: {}", sessionId, record.getFileId(), e);
            record.addError("Persistence", e.getMessage());
            record.setAllStagesSuccess(false);
        }
    }
}