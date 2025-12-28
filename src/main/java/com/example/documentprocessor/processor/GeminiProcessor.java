package com.example.documentprocessor.processor;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.model.ProcessingResult;
import com.example.documentprocessor.service.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiProcessor implements Processor {

    private final GeminiService geminiService;

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = exchange.getIn().getHeader("operation", String.class);
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

        if (record == null) {
            throw new IllegalArgumentException("DocumentRecord is required");
        }

        ProcessingResult result;

        switch (operation) {
            case "ocr":
                result = performOcr(exchange, record);
                break;
            case "translate":
                result = performTranslation(exchange, record);
                break;
            default:
                throw new IllegalArgumentException("Unknown Gemini operation: " + operation);
        }

        if (!result.isSuccess()) {
            record.addError(result.getStage(), result.getError());
        } else {
            updateRecordWithResult(record, result, operation);
        }

        exchange.getIn().setBody(record);
    }

    private ProcessingResult performOcr(Exchange exchange, DocumentRecord record) {
        try {
            // This would need the file data from a previous step
            // For now, assume file data is available in the record or headers
            byte[] fileData = exchange.getIn().getHeader("fileData", byte[].class);
            String mimeType = exchange.getIn().getHeader("mimeType", String.class);
            String prompt = exchange.getIn().getHeader("ocrPrompt", String.class);

            if (fileData == null || mimeType == null) {
                return ProcessingResult.builder()
                    .success(false)
                    .error("File data or MIME type not available")
                    .stage("OCR Preparation")
                    .build();
            }

            // Upload file
            ProcessingResult uploadResult = geminiService.uploadFile(fileData, mimeType, record.getSelectedKey());
            if (!uploadResult.isSuccess()) {
                return uploadResult;
            }

            String fileUri = "files/" + uploadResult.getFileId();

            // Generate content
            ProcessingResult generateResult = geminiService.generateContent(prompt, fileUri, record.getSelectedKey());
            if (!generateResult.isSuccess()) {
                return generateResult;
            }

            // Cleanup
            geminiService.cleanupFile(uploadResult.getFileId(), record.getSelectedKey());

            return ProcessingResult.builder()
                .success(true)
                .extractedText(generateResult.getExtractedText())
                .fileId(record.getFileId())
                .bookName(record.getBookName())
                .build();

        } catch (Exception e) {
            log.error("Error performing OCR", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("OCR")
                .build();
        }
    }

    private ProcessingResult performTranslation(Exchange exchange, DocumentRecord record) {
        try {
            String sourceText = record.getExtractedText();
            String targetLanguage = exchange.getIn().getHeader("targetLanguage", String.class);
            String prompt = exchange.getIn().getHeader("translationPrompt", String.class);
            byte[] fileData = exchange.getIn().getHeader("fileData", byte[].class);

            if (sourceText == null || targetLanguage == null) {
                return ProcessingResult.builder()
                    .success(false)
                    .error("Source text or target language not available")
                    .stage("Translation Preparation")
                    .build();
            }

            ProcessingResult uploadResult = null;
            if (fileData != null) {
                // Upload DOCX file for context
                uploadResult = geminiService.uploadFile(
                    fileData,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    record.getSelectedKey());
                if (!uploadResult.isSuccess()) {
                    return uploadResult;
                }
            }

            String fileUri = uploadResult != null ? "files/" + uploadResult.getFileId() : null;

            // Generate translation
            ProcessingResult generateResult = geminiService.generateContent(prompt, fileUri, record.getSelectedKey());
            if (!generateResult.isSuccess()) {
                return generateResult;
            }

            // Cleanup uploaded file if any
            if (uploadResult != null) {
                geminiService.cleanupFile(uploadResult.getFileId(), record.getSelectedKey());
            }

            return ProcessingResult.builder()
                .success(true)
                .translations(Map.of(targetLanguage, generateResult.getExtractedText()))
                .targetLanguage(targetLanguage)
                .build();

        } catch (Exception e) {
            log.error("Error performing translation", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("Translation")
                .build();
        }
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