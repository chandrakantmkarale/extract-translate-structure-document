package com.example.documentprocessor.processor;

import com.example.documentprocessor.model.DocumentRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrchestratorProcessor {

    public void processInitialRequest(Exchange exchange) {
        DocumentRecord request = exchange.getIn().getBody(DocumentRecord.class);
        log.info("Received DocumentRecord request: {}", request);
        String csvPath = request.getFileId(); // Using fileId as csvPath for now
        String sessionId = java.util.UUID.randomUUID().toString();
        exchange.getIn().setHeader("sessionId", sessionId);
        exchange.getIn().setHeader("csvPath", csvPath);
        log.info("Starting processing session {} for CSV: {}", sessionId, csvPath);
    }
}