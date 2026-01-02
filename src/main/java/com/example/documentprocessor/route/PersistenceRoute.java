package com.example.documentprocessor.route;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.service.GoogleDriveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersistenceRoute extends RouteBuilder {

    private final GoogleDriveService googleDriveService;
    private final ObjectMapper objectMapper;

    @Value("${app.local-testing:true}")
    private boolean localTesting;

    @Override
    public void configure() throws Exception {

        // Perform persistence route
        from("direct:perform-persistence")
            .routeId("perform-persistence")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Performing persistence")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

                try {
                    // Create structured data for persistence
                    var persistenceData = new java.util.HashMap<String, Object>();
                    persistenceData.put("fileId", record.getFileId());
                    persistenceData.put("bookName", record.getBookName());
                    persistenceData.put("extractedText", record.getExtractedText());
                    persistenceData.put("translations", record.getTranslations());
                    persistenceData.put("processingTimestamp", record.getProcessingEndTime());
                    persistenceData.put("allStagesSuccess", record.getAllStagesSuccess());

                    String jsonContent = objectMapper.writeValueAsString(persistenceData);
                    String fileName = "processed_" + record.getFileId().replaceAll("[^a-zA-Z0-9]", "_") + ".json";

                    var result = savePersistenceData(record, fileName, jsonContent);

                    if (!result.isSuccess()) {
                        record.addError(result.getStage(), result.getError());
                    } else {
                        record.setPersistedFileId(result.getExportedFileId());
                    }

                } catch (Exception e) {
                    record.addError("Persistence", "Failed to create persistence data: " + e.getMessage());
                }

                exchange.getIn().setBody(record);
            })
            .end();
    }

    private com.example.documentprocessor.model.ProcessingResult savePersistenceData(
            DocumentRecord record, String fileName, String content) {

        if (localTesting) {
            // Write to local file
            try {
                java.nio.file.Path outputPath = java.nio.file.Paths.get("./test_data/output", fileName);
                java.nio.file.Files.createDirectories(outputPath.getParent());
                java.nio.file.Files.write(outputPath, content.getBytes());
                return com.example.documentprocessor.model.ProcessingResult.builder()
                    .success(true)
                    .exportedFileId(outputPath.toString())
                    .build();
            } catch (Exception e) {
                return com.example.documentprocessor.model.ProcessingResult.builder()
                    .success(false)
                    .error("Failed to write persistence data: " + e.getMessage())
                    .stage("Persistence")
                    .build();
            }
        } else {
            // Upload to Google Drive
            return googleDriveService.uploadFile(
                content.getBytes(),
                fileName,
                "dummy-folder-id", // TODO: Get actual folder ID
                "application/json",
                "dummy-token" // TODO: Get actual token
            );
        }
    }
}