package com.example.documentprocessor.processor;

import com.example.documentprocessor.model.DocumentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrchestratorProcessor {

    public DocumentRecord processInitialRequest(DocumentRecord request) {
        log.info("Received DocumentRecord request: {}", request);
        String csvPath = request.getFileId(); // Using fileId as csvPath for now
        String sessionId = java.util.UUID.randomUUID().toString();
        request.setSessionId(sessionId);
        request.setCsvPath(csvPath);
        log.info("Starting processing session {} for CSV: {}", sessionId, csvPath);
        return request;
    }
}