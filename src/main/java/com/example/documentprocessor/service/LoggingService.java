package com.example.documentprocessor.service;

import com.example.documentprocessor.model.DocumentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoggingService {

    public void logStageCompletion(DocumentRecord record, String sessionId) {
        log.info("Session {} - Completed stage '{}' for file: {} with success: {}",
            sessionId, record.getCurrentStage(), record.getFileId(), record.getAllStagesSuccess());
    }
}