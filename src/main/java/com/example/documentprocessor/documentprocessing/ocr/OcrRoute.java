package com.example.documentprocessor.documentprocessing.ocr;

import com.example.documentprocessor.documentprocessing.config.ApplicationProperties;
import com.example.documentprocessor.shared.model.DocumentRecord;
import com.example.documentprocessor.shared.model.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Route for handling OCR (Optical Character Recognition) processing.
 * Coordinates document fetching, prompt loading, OCR execution, and result export.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OcrRoute extends RouteBuilder {

    private final ApplicationProperties properties;

    @Override
    public void configure() throws Exception {

        // Document fetching route
        from("direct:fetch-document")
            .routeId("fetch-document")
            .process(this::fetchDocument)
            .end();

        // OCR prompt fetching route
        from("direct:fetch-ocr-prompt")
            .routeId("fetch-ocr-prompt")
            .process(this::fetchOcrPrompt)
            .end();

        // OCR execution route
        from("direct:perform-ocr")
            .routeId("perform-ocr")
            .setHeader("operation", constant("ocr"))
            .to("bean:geminiProcessor")
            .end();

        // OCR results export route
        from("direct:export-ocr")
            .routeId("export-ocr")
            .process(this::exportOcrResults)
            .end();
    }

    /**
     * Fetches the document file from the configured source.
     * In local testing mode, reads from local filesystem.
     * In production mode, would download from Google Drive.
     */
    private void fetchDocument(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        String fileId = record.getFileId();

        try {
            ProcessingResult result;
            if (properties.isLocalTesting()) {
                result = fetchDocumentFromLocal(record, fileId);
            } else {
                result = fetchDocumentFromGoogleDrive(record, fileId);
            }

            if (!result.isSuccess()) {
                record.addError(result.getStage(), result.getError());
            } else {
                exchange.getIn().setHeader("fileData", result.getFileData());
                exchange.getIn().setHeader("mimeType", result.getMimeType());
            }

        } catch (Exception e) {
            record.addError("Document Fetch", "Failed to fetch document: " + e.getMessage());
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Fetches document from local filesystem.
     */
    private ProcessingResult fetchDocumentFromLocal(DocumentRecord record, String fileId) {
        try {
            Path filePath = Paths.get(properties.getLocal().getDocsPath(), fileId);
            if (!Files.exists(filePath)) {
                return ProcessingResult.failure("Document Fetch",
                    "Local file not found: " + filePath);
            }

            byte[] fileData = Files.readAllBytes(filePath);
            String mimeType = Files.probeContentType(filePath);
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            return ProcessingResult.builder()
                .success(true)
                .fileData(fileData)
                .mimeType(mimeType)
                .fileId(fileId)
                .stage("Document Fetch")
                .build();

        } catch (Exception e) {
            return ProcessingResult.failure("Document Fetch",
                "Error reading local file: " + e.getMessage());
        }
    }

    /**
     * Fetches document from Google Drive.
     * TODO: Implement Google Drive integration
     */
    private ProcessingResult fetchDocumentFromGoogleDrive(DocumentRecord record, String fileId) {
        // TODO: Implement Google Drive document download
        return ProcessingResult.failure("Document Fetch",
            "Google Drive document fetching not implemented yet");
    }

    /**
     * Fetches the OCR prompt from the configured source.
     */
    private void fetchOcrPrompt(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

        try {
            String prompt;
            if (properties.isLocalTesting()) {
                prompt = fetchOcrPromptFromLocal();
            } else {
                prompt = fetchOcrPromptFromGoogleDrive();
            }

            exchange.getIn().setHeader("ocrPrompt", prompt);

        } catch (Exception e) {
            record.addError("OCR Prompt Fetch", "Failed to fetch OCR prompt: " + e.getMessage());
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Fetches OCR prompt from local file.
     */
    private String fetchOcrPromptFromLocal() throws Exception {
        Path promptPath = Paths.get(properties.getLocal().getDocsPath(),
            properties.getFiles().getPromptOcrFilename());

        if (!Files.exists(promptPath)) {
            // Return default prompt if file doesn't exist
            return "Extract all text from this document. Provide the text content exactly as it appears.";
        }

        return new String(Files.readAllBytes(promptPath));
    }

    /**
     * Fetches OCR prompt from Google Drive.
     * TODO: Implement Google Drive integration
     */
    private String fetchOcrPromptFromGoogleDrive() {
        // TODO: Implement Google Drive prompt fetching
        return "Extract all text from this document. Provide the text content exactly as it appears.";
    }

    /**
     * Exports OCR results to the configured destination.
     */
    private void exportOcrResults(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

        if (record.getExtractedText() != null && !record.getExtractedText().isEmpty()) {
            ProcessingResult result;
            String fileName = "ocr_" + record.getFileId().replaceAll("[^a-zA-Z0-9]", "_") + ".txt";

            if (properties.isLocalTesting()) {
                result = exportOcrToLocal(record, fileName);
            } else {
                result = exportOcrToGoogleDrive(record, fileName);
            }

            if (!result.isSuccess()) {
                record.addError(result.getStage(), result.getError());
            } else {
                record.setExtractedFileId(result.getExportedFileId());
            }
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Exports OCR results to local filesystem.
     */
    private ProcessingResult exportOcrToLocal(DocumentRecord record, String fileName) {
        try {
            Path outputPath = Paths.get(properties.getLocal().getOutputPath(), fileName);
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, record.getExtractedText().getBytes());

            return ProcessingResult.builder()
                .success(true)
                .exportedFileId(outputPath.toString())
                .stage("OCR Export")
                .build();

        } catch (Exception e) {
            return ProcessingResult.failure("OCR Export",
                "Failed to write OCR output: " + e.getMessage());
        }
    }

    /**
     * Exports OCR results to Google Drive.
     * TODO: Implement Google Drive integration
     */
    private ProcessingResult exportOcrToGoogleDrive(DocumentRecord record, String fileName) {
        // TODO: Implement Google Drive document creation
        return ProcessingResult.failure("OCR Export",
            "Google Drive OCR export not implemented yet");
    }
}