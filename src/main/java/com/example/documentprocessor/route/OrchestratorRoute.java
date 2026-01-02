package com.example.documentprocessor.route;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.processor.CsvProcessor;
import com.example.documentprocessor.service.KeyRotationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrchestratorRoute extends RouteBuilder {

    private final CsvProcessor csvProcessor;
    private final KeyRotationService keyRotationService;

    @Override
    public void configure() throws Exception {

        // Main orchestrator route - REST endpoint to start processing
        rest("/process")
            .post()
            .consumes("application/json")
            .produces("application/json")
            .to("direct:orchestrator-main");

        from("direct:orchestrator-main")
            .routeId("orchestrator-main")
            .unmarshal().json(JsonLibrary.Jackson, DocumentRecord.class)
            .process(exchange -> {
                DocumentRecord request = exchange.getIn().getBody(DocumentRecord.class);
                String csvPath = request.getFileId(); // Using fileId as csvPath for now
                String sessionId = java.util.UUID.randomUUID().toString();
                exchange.getIn().setHeader("sessionId", sessionId);
                exchange.getIn().setHeader("csvPath", csvPath);
                log.info("Starting processing session {} for CSV: {}", sessionId, csvPath);
            })
            .to("direct:read-csv")
            .to("direct:validate-csv")
            .to("direct:process-batch")
            .to("direct:write-results")
            .marshal().json(JsonLibrary.Jackson);

        // Read CSV file
        from("direct:read-csv")
            .routeId("read-csv")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Reading CSV")
            .setHeader("operation", constant("read"))
            .process(csvProcessor)
            .end();

        // Validate CSV structure
        from("direct:validate-csv")
            .routeId("validate-csv")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Validating CSV")
            .setHeader("operation", constant("validate"))
            .process(csvProcessor)
            .end();

        // Process documents in batch
        from("direct:process-batch")
            .routeId("process-batch")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Processing batch")
            .split(body()).parallelProcessing()
                .process(exchange -> {
                    DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                    record.setExecutionId(exchange.getExchangeId());
                    record.setWorkflowName("Orchestrator");
                    record.setCurrentStage("Key Rotation");
                    record.setAllStagesSuccess(true);
                    exchange.getIn().setBody(record);
                })
                .to("direct:key-rotation")
                .choice()
                    .when(exchange -> {
                        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                        return record.getAllStagesSuccess() && record.getSelectedKey() != null;
                    })
                        .to("direct:ocr-processing")
                        .choice()
                            .when(exchange -> {
                                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                                return record.getAllStagesSuccess();
                            })
                                .to("direct:translate-processing")
                                .choice()
                                    .when(exchange -> {
                                        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                                        return record.getAllStagesSuccess();
                                    })
                                        .to("direct:structure-processing")
                                        .to("direct:persistence-processing")
                                    .endChoice()
                                .endChoice()
                            .endChoice()
                        .endChoice()
                    .endChoice()
                .end()
            .end()
            .aggregate(constant(true), (oldExchange, newExchange) -> {
                @SuppressWarnings("unchecked")
                List<DocumentRecord> records = oldExchange == null ?
                    newExchange.getIn().getBody(List.class) :
                    oldExchange.getIn().getBody(List.class);
                return newExchange;
            })
            .completionSize(header("CamelSplitSize"))
            .end();

        // Key rotation sub-route
        from("direct:key-rotation")
            .routeId("key-rotation")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                var keyResult = keyRotationService.getNextKey();
                if (keyResult.isSuccess()) {
                    record.setSelectedKey(keyResult.getSelectedKey());
                    log.info("Selected API key for record: {}", record.getFileId());
                } else {
                    record.addError("Key Rotation", keyResult.getError());
                }
                exchange.getIn().setBody(record);
            })
            .to("direct:log-stage")
            .end();

        // OCR processing sub-route
        from("direct:ocr-processing")
            .routeId("ocr-processing")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                record.setCurrentStage("OCR");
                log.info("Session ${header.sessionId} - Starting OCR processing for file: {}", record.getFileId());
                exchange.getIn().setBody(record);
            })
            .to("direct:fetch-document")
            .to("direct:fetch-ocr-prompt")
            .to("direct:perform-ocr")
            .to("direct:export-ocr")
            .to("direct:log-stage")
            .end();

        // Translation processing sub-route
        from("direct:translate-processing")
            .routeId("translate-processing")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                record.setCurrentStage("Translation");
                log.info("Session ${header.sessionId} - Starting translation processing for file: {}", record.getFileId());
                exchange.getIn().setBody(record);
            })
            .to("direct:fetch-translation-prompt")
            .to("direct:perform-translation")
            .to("direct:export-translation")
            .to("direct:log-stage")
            .end();

        // Structure processing sub-route
        from("direct:structure-processing")
            .routeId("structure-processing")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                record.setCurrentStage("Structure");
                log.info("Session ${header.sessionId} - Starting structure processing for file: {}", record.getFileId());
                exchange.getIn().setBody(record);
            })
            .to("direct:perform-structuring")
            .to("direct:log-stage")
            .end();

        // Persistence processing sub-route
        from("direct:persistence-processing")
            .routeId("persistence-processing")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                record.setCurrentStage("Persistence");
                record.setProcessingEndTime(java.time.LocalDateTime.now());
                log.info("Session ${header.sessionId} - Starting persistence processing for file: {}", record.getFileId());
                exchange.getIn().setBody(record);
            })
            .to("direct:perform-persistence")
            .to("direct:log-stage")
            .end();

        // Write final results to CSV
        from("direct:write-results")
            .routeId("write-results")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Writing results")
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                List<DocumentRecord> records = exchange.getIn().getBody(List.class);
                String csvPath = exchange.getIn().getHeader("csvPath", String.class);

                // Set final status for each record
                records.forEach(record -> {
                    if (record.getAllStagesSuccess() && !record.hasErrors()) {
                        record.setStatus("processed");
                    } else {
                        record.setStatus("failed");
                    }
                });

                exchange.getIn().setBody(records);
                exchange.getIn().setHeader("filePath", csvPath);
            })
            .setHeader("operation", constant("write"))
            .process(csvProcessor)
            .end();

        // Placeholder routes for sub-workflows (to be implemented)
        from("direct:fetch-document").routeId("fetch-document").log(LoggingLevel.INFO, "Session ${header.sessionId} - Fetching document: ${body.fileId}").end();
        from("direct:fetch-ocr-prompt").routeId("fetch-ocr-prompt").log(LoggingLevel.INFO, "Session ${header.sessionId} - Fetching OCR prompt for file: ${body.fileId}").end();
        from("direct:perform-ocr").routeId("perform-ocr").log(LoggingLevel.INFO, "Session ${header.sessionId} - Performing OCR on file: ${body.fileId}").end();
        from("direct:export-ocr").routeId("export-ocr").log(LoggingLevel.INFO, "Session ${header.sessionId} - Exporting OCR results for file: ${body.fileId}").end();
        from("direct:fetch-translation-prompt").routeId("fetch-translation-prompt").log(LoggingLevel.INFO, "Session ${header.sessionId} - Fetching translation prompt for file: ${body.fileId}").end();
        from("direct:perform-translation").routeId("perform-translation").log(LoggingLevel.INFO, "Session ${header.sessionId} - Performing translation on file: ${body.fileId}").end();
        from("direct:export-translation").routeId("export-translation").log(LoggingLevel.INFO, "Session ${header.sessionId} - Exporting translation results for file: ${body.fileId}").end();
        from("direct:perform-structuring").routeId("perform-structuring").log(LoggingLevel.INFO, "Session ${header.sessionId} - Performing structuring on file: ${body.fileId}").end();
        from("direct:perform-persistence").routeId("perform-persistence").log(LoggingLevel.INFO, "Session ${header.sessionId} - Performing persistence for file: ${body.fileId}").end();
        from("direct:log-stage").routeId("log-stage").log(LoggingLevel.INFO, "Session ${header.sessionId} - Completed stage for file: ${body.fileId}").end();
    }
}