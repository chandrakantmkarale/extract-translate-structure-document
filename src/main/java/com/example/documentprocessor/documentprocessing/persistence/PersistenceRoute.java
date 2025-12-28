package com.example.documentprocessor.documentprocessing.persistence;

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
 * Route for handling document persistence operations.
 * Manages saving processing results and metadata to configured storage.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PersistenceRoute extends RouteBuilder {

    private final ApplicationProperties properties;

    @Override
    public void configure() throws Exception {

        // Processing results persistence route
        from("direct:persist-results")
            .routeId("persist-results")
            .process(this::persistProcessingResults)
            .end();

        // Error log persistence route
        from("direct:persist-error-log")
            .routeId("persist-error-log")
            .process(this::persistErrorLog)
            .end();

        // Batch processing summary route
        from("direct:persist-batch-summary")
            .routeId("persist-batch-summary")
            .process(this::persistBatchSummary)
            .end();
    }

    /**
     * Persists processing results to the configured destination.
     */
    private void persistProcessingResults(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

        ProcessingResult result;
        if (properties.isLocalTesting()) {
            result = persistResultsToLocal(record);
        } else {
            result = persistResultsToGoogleDrive(record);
        }

        if (!result.isSuccess()) {
            record.addError(result.getStage(), result.getError());
        } else {
            record.setPersistedFileId(result.getExportedFileId());
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Persists processing results to local filesystem.
     */
    private ProcessingResult persistResultsToLocal(DocumentRecord record) {
        try {
            String fileName = "results_" + record.getFileId().replaceAll("[^a-zA-Z0-9]", "_") + ".json";
            Path outputPath = Paths.get(properties.getLocal().getOutputPath(), fileName);
            Files.createDirectories(outputPath.getParent());

            // Create JSON representation of the record
            String jsonContent = createResultsJson(record);
            Files.write(outputPath, jsonContent.getBytes());

            return ProcessingResult.builder()
                .success(true)
                .exportedFileId(outputPath.toString())
                .stage("Results Persistence")
                .build();

        } catch (Exception e) {
            return ProcessingResult.failure("Results Persistence",
                "Failed to persist results: " + e.getMessage());
        }
    }

    /**
     * Persists processing results to Google Drive.
     * TODO: Implement Google Drive integration
     */
    private ProcessingResult persistResultsToGoogleDrive(DocumentRecord record) {
        // TODO: Implement Google Drive document creation
        return ProcessingResult.failure("Results Persistence",
            "Google Drive results persistence not implemented yet");
    }

    /**
     * Persists error log to the configured destination.
     */
    private void persistErrorLog(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

        if (record.hasErrors()) {
            ProcessingResult result;
            if (properties.isLocalTesting()) {
                result = persistErrorLogToLocal(record);
            } else {
                result = persistErrorLogToGoogleDrive(record);
            }

            if (!result.isSuccess()) {
                log.error("Failed to persist error log for file {}: {}",
                    record.getFileId(), result.getError());
            }
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Persists error log to local filesystem.
     */
    private ProcessingResult persistErrorLogToLocal(DocumentRecord record) {
        try {
            String fileName = "error_" + record.getFileId().replaceAll("[^a-zA-Z0-9]", "_") + ".txt";
            Path outputPath = Paths.get(properties.getLocal().getOutputPath(), "errors", fileName);
            Files.createDirectories(outputPath.getParent());

            StringBuilder errorContent = new StringBuilder();
            errorContent.append("Error Log for File: ").append(record.getFileId()).append("\n");
            errorContent.append("Processing Date: ").append(record.getProcessingStartTime()).append("\n\n");

            record.getErrors().forEach(error -> {
                errorContent.append("Stage: ").append(error.getStage()).append("\n");
                errorContent.append("Error: ").append(error.getError()).append("\n");
                errorContent.append("Timestamp: ").append(error.getTimestamp()).append("\n\n");
            });

            Files.write(outputPath, errorContent.toString().getBytes());

            return ProcessingResult.builder()
                .success(true)
                .exportedFileId(outputPath.toString())
                .stage("Error Log Persistence")
                .build();

        } catch (Exception e) {
            return ProcessingResult.failure("Error Log Persistence",
                "Failed to persist error log: " + e.getMessage());
        }
    }

    /**
     * Persists error log to Google Drive.
     * TODO: Implement Google Drive integration
     */
    private ProcessingResult persistErrorLogToGoogleDrive(DocumentRecord record) {
        // TODO: Implement Google Drive document creation
        return ProcessingResult.failure("Error Log Persistence",
            "Google Drive error log persistence not implemented yet");
    }

    /**
     * Persists batch processing summary.
     */
    private void persistBatchSummary(org.apache.camel.Exchange exchange) {
        @SuppressWarnings("unchecked")
        java.util.List<DocumentRecord> batchRecords =
            (java.util.List<DocumentRecord>) exchange.getIn().getBody();

        ProcessingResult result;
        if (properties.isLocalTesting()) {
            result = persistBatchSummaryToLocal(batchRecords);
        } else {
            result = persistBatchSummaryToGoogleDrive(batchRecords);
        }

        if (!result.isSuccess()) {
            log.error("Failed to persist batch summary: {}", result.getError());
        }

        exchange.getIn().setBody(batchRecords);
    }

    /**
     * Persists batch summary to local filesystem.
     */
    private ProcessingResult persistBatchSummaryToLocal(java.util.List<DocumentRecord> batchRecords) {
        try {
            String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "batch_summary_" + timestamp + ".json";
            Path outputPath = Paths.get(properties.getLocal().getOutputPath(), fileName);
            Files.createDirectories(outputPath.getParent());

            String jsonContent = createBatchSummaryJson(batchRecords);
            Files.write(outputPath, jsonContent.getBytes());

            return ProcessingResult.builder()
                .success(true)
                .exportedFileId(outputPath.toString())
                .stage("Batch Summary Persistence")
                .build();

        } catch (Exception e) {
            return ProcessingResult.failure("Batch Summary Persistence",
                "Failed to persist batch summary: " + e.getMessage());
        }
    }

    /**
     * Persists batch summary to Google Drive.
     * TODO: Implement Google Drive integration
     */
    private ProcessingResult persistBatchSummaryToGoogleDrive(java.util.List<DocumentRecord> batchRecords) {
        // TODO: Implement Google Drive document creation
        return ProcessingResult.failure("Batch Summary Persistence",
            "Google Drive batch summary persistence not implemented yet");
    }

    /**
     * Creates JSON representation of processing results.
     */
    private String createResultsJson(DocumentRecord record) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"fileId\": \"").append(escapeJson(record.getFileId())).append("\",\n");
        json.append("  \"processingDate\": \"").append(record.getProcessingStartTime()).append("\",\n");

        if (record.getExtractedText() != null) {
            json.append("  \"extractedText\": \"").append(escapeJson(record.getExtractedText())).append("\",\n");
        }

        if (record.getTranslatedText() != null) {
            json.append("  \"translatedText\": \"").append(escapeJson(record.getTranslatedText())).append("\",\n");
        }

        if (record.getExtractedFileId() != null) {
            json.append("  \"extractedFileId\": \"").append(escapeJson(record.getExtractedFileId())).append("\",\n");
        }

        if (record.getTranslatedFileId() != null) {
            json.append("  \"translatedFileId\": \"").append(escapeJson(record.getTranslatedFileId())).append("\",\n");
        }

        json.append("  \"errors\": [\n");
        for (int i = 0; i < record.getErrors().size(); i++) {
            var error = record.getErrors().get(i);
            json.append("    {\n");
            json.append("      \"stage\": \"").append(escapeJson(error.getStage())).append("\",\n");
            json.append("      \"message\": \"").append(escapeJson(error.getError())).append("\",\n");
            json.append("      \"timestamp\": \"").append(error.getTimestamp()).append("\"\n");
            json.append("    }");
            if (i < record.getErrors().size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}");

        return json.toString();
    }

    /**
     * Creates JSON representation of batch summary.
     */
    private String createBatchSummaryJson(java.util.List<DocumentRecord> batchRecords) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"batchSummary\": {\n");
        json.append("    \"totalFiles\": ").append(batchRecords.size()).append(",\n");

        long successCount = batchRecords.stream().filter(r -> !r.hasErrors()).count();
        json.append("    \"successfulFiles\": ").append(successCount).append(",\n");
        json.append("    \"failedFiles\": ").append(batchRecords.size() - successCount).append(",\n");
        json.append("    \"processingDate\": \"")
            .append(java.time.LocalDateTime.now()).append("\"\n");
        json.append("  },\n");

        json.append("  \"files\": [\n");
        for (int i = 0; i < batchRecords.size(); i++) {
            var record = batchRecords.get(i);
            json.append("    {\n");
            json.append("      \"fileId\": \"").append(escapeJson(record.getFileId())).append("\",\n");
            json.append("      \"hasErrors\": ").append(record.hasErrors()).append(",\n");
            json.append("      \"errorCount\": ").append(record.getErrors().size()).append("\n");
            json.append("    }");
            if (i < batchRecords.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}");

        return json.toString();
    }

    /**
     * Escapes special characters in JSON strings.
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}