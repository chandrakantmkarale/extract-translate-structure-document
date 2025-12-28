package com.example.documentprocessor.route;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.model.ProcessingResult;
import com.example.documentprocessor.processor.GeminiProcessor;
import com.example.documentprocessor.service.GeminiService;
import com.example.documentprocessor.service.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OcrRoute extends RouteBuilder {

    private final GeminiService geminiService;
    private final GoogleDriveService googleDriveService;
    private final GeminiProcessor geminiProcessor;

    @Value("${app.local-testing:true}")
    private boolean localTesting;

    @Value("${app.local.docs-path:./test_data/input}")
    private String localDocsPath;

    @Value("${google.drive.api.base-url:https://www.googleapis.com/drive/v3}")
    private String driveApiBaseUrl;

    @Override
    public void configure() throws Exception {

        // Fetch document route
        from("direct:fetch-document")
            .routeId("fetch-document")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                String fileId = record.getFileId();

                ProcessingResult result;
                if (localTesting) {
                    // Read from local filesystem
                    java.nio.file.Path filePath = java.nio.file.Paths.get(localDocsPath, fileId);
                    if (java.nio.file.Files.exists(filePath)) {
                        byte[] fileData = java.nio.file.Files.readAllBytes(filePath);
                        String mimeType = java.nio.file.Files.probeContentType(filePath);
                        result = ProcessingResult.builder()
                            .success(true)
                            .fileData(fileData)
                            .mimeType(mimeType != null ? mimeType : "application/octet-stream")
                            .fileId(fileId)
                            .build();
                    } else {
                        result = ProcessingResult.builder()
                            .success(false)
                            .error("Local file not found: " + filePath)
                            .stage("Document Fetch")
                            .build();
                    }
                } else {
                    // Download from Google Drive
                    result = googleDriveService.downloadFile(fileId, "dummy-token"); // TODO: Get actual token
                }

                if (!result.isSuccess()) {
                    record.addError(result.getStage(), result.getError());
                } else {
                    exchange.getIn().setHeader("fileData", result.getFileData());
                    exchange.getIn().setHeader("mimeType", result.getMimeType());
                }

                exchange.getIn().setBody(record);
            })
            .end();

        // Fetch OCR prompt route
        from("direct:fetch-ocr-prompt")
            .routeId("fetch-ocr-prompt")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

                String prompt;
                if (localTesting) {
                    // Read from local file
                    try {
                        java.nio.file.Path promptPath = java.nio.file.Paths.get(localDocsPath, "prompt_ocr.txt");
                        if (java.nio.file.Files.exists(promptPath)) {
                            prompt = new String(java.nio.file.Files.readAllBytes(promptPath));
                        } else {
                            prompt = "Extract all text from this document. Provide the text content exactly as it appears.";
                        }
                    } catch (Exception e) {
                        log.warn("Error reading OCR prompt, using default", e);
                        prompt = "Extract all text from this document. Provide the text content exactly as it appears.";
                    }
                } else {
                    // TODO: Download from Google Drive
                    prompt = "Extract all text from this document. Provide the text content exactly as it appears.";
                }

                exchange.getIn().setHeader("ocrPrompt", prompt);
                exchange.getIn().setBody(record);
            })
            .end();

        // Perform OCR route
        from("direct:perform-ocr")
            .routeId("perform-ocr")
            .setHeader("operation", constant("ocr"))
            .process(geminiProcessor)
            .end();

        // Export OCR results route
        from("direct:export-ocr")
            .routeId("export-ocr")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

                if (record.getExtractedText() != null && !record.getExtractedText().isEmpty()) {
                    ProcessingResult result;
                    String fileName = "ocr_" + record.getFileId().replaceAll("[^a-zA-Z0-9]", "_") + ".txt";

                    if (localTesting) {
                        // Write to local file
                        try {
                            java.nio.file.Path outputPath = java.nio.file.Paths.get(localDocsPath, "../output", fileName);
                            java.nio.file.Files.createDirectories(outputPath.getParent());
                            java.nio.file.Files.write(outputPath, record.getExtractedText().getBytes());
                            result = ProcessingResult.builder()
                                .success(true)
                                .exportedFileId(outputPath.toString())
                                .build();
                        } catch (Exception e) {
                            result = ProcessingResult.builder()
                                .success(false)
                                .error("Failed to write OCR output: " + e.getMessage())
                                .stage("OCR Export")
                                .build();
                        }
                    } else {
                        // Upload to Google Drive
                        result = googleDriveService.createDocument(
                            fileName,
                            record.getExtractedText(),
                            "dummy-folder-id", // TODO: Get actual folder ID
                            "dummy-token" // TODO: Get actual token
                        );
                    }

                    if (!result.isSuccess()) {
                        record.addError(result.getStage(), result.getError());
                    } else {
                        record.setExtractedFileId(result.getExportedFileId());
                    }
                }

                exchange.getIn().setBody(record);
            })
            .end();
    }
}