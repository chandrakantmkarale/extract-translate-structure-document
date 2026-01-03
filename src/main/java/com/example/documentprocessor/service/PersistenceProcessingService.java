package com.example.documentprocessor.service;

import com.example.documentprocessor.model.DocumentRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersistenceProcessingService {

    private final GoogleDriveService googleDriveService;

    private final String outputFolderId = "";

    public void processPersistence(DocumentRecord record, String sessionId) {
        try {
            log.info("Session {} - Starting persistence processing for file: {}", sessionId, record.getFileId());

            // TODO: Implement persistence logic - save translations to Google Drive
            // For now, this is a placeholder that marks the stage as successful

            log.info("Session {} - Persistence processing completed for file: {}", sessionId, record.getFileId());

        } catch (Exception e) {
            log.error("Session {} - Error during persistence processing for file: {}", sessionId, record.getFileId(), e);
            record.addError("Persistence", e.getMessage());
        }
    }
}