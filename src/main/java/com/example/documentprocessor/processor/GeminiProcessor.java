package com.example.documentprocessor.processor;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.model.ProcessingResult;
import com.example.documentprocessor.service.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiProcessor {

    private final GeminiService geminiService;

    public ProcessingResult performOcr(DocumentRecord record, byte[] fileData, String mimeType, String prompt) {
        log.info("Processing OCR operation for record: {}", record.getFileId());

        return geminiService.performOcr(fileData, mimeType, prompt, record.getSelectedKey());
    }

    public ProcessingResult performTranslation(DocumentRecord record, String sourceText, String targetLanguage, String prompt) {
        log.info("Processing translation operation for record: {} to language: {}", record.getFileId(), targetLanguage);

        return geminiService.performTranslation(sourceText, targetLanguage, prompt, record.getSelectedKey());
    }

    private void updateRecordWithResult(DocumentRecord record, ProcessingResult result, String operation) {
        switch (operation) {
            case "ocr":
                record.setExtractedText(result.getExtractedText());
                record.setExtractedFileId(result.getFileId());
                break;
            case "translate":
                if (record.getTranslations() == null) {
                    record.setTranslations(new HashMap<>());
                }
                record.getTranslations().put(result.getTargetLanguage(), result.getExtractedText());
                break;
        }
    }
}