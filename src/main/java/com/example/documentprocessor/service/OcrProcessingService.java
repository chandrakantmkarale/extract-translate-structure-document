package com.example.documentprocessor.service;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.processor.GeminiProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class OcrProcessingService {

    private final Optional<GoogleDriveService> googleDriveService;
    private final GeminiProcessor geminiProcessor;

    @Value("${app.files.extract-text-from-pdf-id:}")
    private String extractTextFromPdfPromptFileId;

    public OcrProcessingService(Optional<GoogleDriveService> googleDriveService, GeminiProcessor geminiProcessor) {
        this.googleDriveService = googleDriveService;
        this.geminiProcessor = geminiProcessor;
    }

    public void processOcr(DocumentRecord record, String sessionId) {
        if (googleDriveService.isEmpty()) {
            log.error("Session {} - Google Drive service not available", sessionId);
            record.addError("OCR", "Google Drive service not available");
            record.setAllStagesSuccess(false);
            return;
        }

        try {
            log.info("Session {} - Starting OCR processing for file: {}", sessionId, record.getFileId());

            // Download the file from Google Drive
            var downloadResult = googleDriveService.get().downloadFile(record.getFileId());
            if (!downloadResult.isSuccess()) {
                log.error("Session {} - Failed to download file: {}", sessionId, downloadResult.getError());
                record.addError("OCR", downloadResult.getError());
                record.setAllStagesSuccess(false);
                return;
            }

            // Download and read OCR prompt from Google Drive
            var promptDownloadResult = googleDriveService.get().downloadAndExtractText(extractTextFromPdfPromptFileId);
            if (!promptDownloadResult.isSuccess()) {
                log.error("Session {} - Failed to download and extract prompt text: {}", sessionId, promptDownloadResult.getError());
                record.addError("OCR", promptDownloadResult.getError());
                record.setAllStagesSuccess(false);
                return;
            }

            String prompt = promptDownloadResult.getExtractedText();

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