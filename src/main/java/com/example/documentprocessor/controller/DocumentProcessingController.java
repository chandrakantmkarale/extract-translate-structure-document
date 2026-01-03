package com.example.documentprocessor.controller;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.service.DocumentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class DocumentProcessingController {

    private final DocumentProcessingService documentProcessingService;

    public static class ProcessRequest {
        private String fileId;

        public String getFileId() {
            return fileId;
        }

        public void setFileId(String fileId) {
            this.fileId = fileId;
        }
    }

    @PostMapping("/process")
    public ResponseEntity<List<DocumentRecord>> processDocuments(@RequestBody ProcessRequest request) {
        log.info("Received processing request: {}", request);
        try {
            DocumentRecord record = DocumentRecord.builder()
                .fileId(request.getFileId())
                .build();
            List<DocumentRecord> results = documentProcessingService.processDocuments(record);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error processing documents", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}