package com.example.documentprocessor.service;

import com.example.documentprocessor.model.DocumentRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrProcessingService {

    private final GoogleDriveService googleDriveService;
    private final GeminiService geminiService;

    private final String ocrPromptFilePath = "./test_data/input/prompt_ocr.txt";

    public void processOcr(DocumentRecord record, String sessionId) {
        try {
            log.info("Session {} - Starting OCR processing for file: {}", sessionId, record.getFileId());

            // TODO: Implement OCR processing logic
            // For now, this is a placeholder

            log.info("Session {} - OCR processing completed for file: {}", sessionId, record.getFileId());

        } catch (Exception e) {
            log.error("Session {} - Error during OCR processing for file: {}", sessionId, record.getFileId(), e);
            record.addError("OCR", e.getMessage());
        }
    }
}