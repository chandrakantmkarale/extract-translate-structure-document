package com.example.documentprocessor.service;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.processor.GeminiProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrProcessingService {

    private final GoogleDriveService googleDriveService;
    private final GeminiProcessor geminiProcessor;

    private final String ocrPromptFilePath = "./test_data/input/prompt_ocr.txt";

    public void processOcr(DocumentRecord record, String sessionId) {
        try {
            log.info("Session {} - Starting OCR processing for file: {}", sessionId, record.getFileId());

            // Download the file from Google Drive
            var downloadResult = googleDriveService.downloadFile(record.getFileId());
            if (!downloadResult.isSuccess()) {
                log.error("Session {} - Failed to download file: {}", sessionId, downloadResult.getError());
                record.addError("OCR", downloadResult.getError());
                record.setAllStagesSuccess(false);
                return;
            }

            // Read OCR prompt
            String prompt = Files.readString(Paths.get(ocrPromptFilePath));

            // Perform OCR using Gemini
            var ocrResult = geminiProcessor.performOcr(record, downloadResult.getFileData(),
                downloadResult.getMimeType(), prompt);

            if (ocrResult.isSuccess()) {
                record.setExtractedText(ocrResult.getExtractedText());
                record.setExtractedFileId(record.getFileId());
                log.info("Session {} - OCR completed successfully for file: {}", sessionId, record.getFileId());
            } else {
                log.error("Session {} - OCR failed: {}", sessionId, ocrResult.getError());
                record.addError("OCR", ocrResult.getError());
                record.setAllStagesSuccess(false);
            }

        } catch (Exception e) {
            log.error("Session {} - Error during OCR processing for file: {}", sessionId, record.getFileId(), e);
            record.addError("OCR", e.getMessage());
            record.setAllStagesSuccess(false);
        }
    }
}