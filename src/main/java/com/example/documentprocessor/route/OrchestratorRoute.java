package com.example.documentprocessor.route;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.processor.CsvProcessor;
import com.example.documentprocessor.processor.OrchestratorProcessor;
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

    static {
        System.out.println("OrchestratorRoute class loaded!");
    }

    private final CsvProcessor csvProcessor;
    private final KeyRotationService keyRotationService;
    private final OrchestratorProcessor orchestratorProcessor;

    @Override
    public void configure() throws Exception {
        log.info("Configuring OrchestratorRoute - setting up REST endpoints");

        // Main orchestrator route - REST endpoint to start processing
        rest("/process")
            .post()
            .consumes("application/json")
            .produces("application/json")
            .to("direct:orchestrator-main");

        log.info("REST endpoint /process configured and mapped to direct:orchestrator-main");

        from("direct:orchestrator-main")
            .routeId("orchestrator-main")
            .unmarshal().json(JsonLibrary.Jackson, DocumentRecord.class)
            .process(orchestratorProcessor::processInitialRequest)
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
            .process(exchange -> {
                @SuppressWarnings("unchecked")
                List<DocumentRecord> records = exchange.getIn().getBody(List.class);
                log.info("Session ${header.sessionId} - About to process {} records in batch", records.size());
                exchange.getIn().setBody(records);
            })
            .split(body()).parallelProcessing()
                .log(LoggingLevel.INFO, "Session ${header.sessionId} - Processing individual record: ${body.fileId}")
                .process(exchange -> {
                    DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                    record.setExecutionId(exchange.getExchangeId());
                    record.setWorkflowName("Orchestrator");
                    record.setCurrentStage("Key Rotation");
                    record.setAllStagesSuccess(true);
                    log.info("Session ${header.sessionId} - Initialized record {} for processing", record.getFileId());
                    exchange.getIn().setBody(record);
                })
                .to("direct:key-rotation")
                .choice()
                    .when(exchange -> {
                        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                        boolean success = record.getAllStagesSuccess() && record.getSelectedKey() != null;
                        log.info("Session ${header.sessionId} - Key rotation check for {}: success={}", record.getFileId(), success);
                        return success;
                    })
                        .log(LoggingLevel.INFO, "Session ${header.sessionId} - Proceeding to OCR for ${body.fileId}")
                        .to("direct:ocr-processing")
                        .choice()
                            .when(exchange -> {
                                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                                boolean success = record.getAllStagesSuccess();
                                log.info("Session ${header.sessionId} - OCR check for {}: success={}", record.getFileId(), success);
                                return success;
                            })
                                .log(LoggingLevel.INFO, "Session ${header.sessionId} - Proceeding to translation for ${body.fileId}")
                                .to("direct:translate-processing")
                                .choice()
                                    .when(exchange -> {
                                        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                                        boolean success = record.getAllStagesSuccess();
                                        log.info("Session ${header.sessionId} - Translation check for {}: success={}", record.getFileId(), success);
                                        return success;
                                    })
                                        .log(LoggingLevel.INFO, "Session ${header.sessionId} - Proceeding to structure processing for ${body.fileId}")
                                        .to("direct:structure-processing")
                                        .log(LoggingLevel.INFO, "Session ${header.sessionId} - Proceeding to persistence for ${body.fileId}")
                                        .to("direct:persistence-processing")
                                    .endChoice()
                                .endChoice()
                            .endChoice()
                        .endChoice()
                    .endChoice()
                .end()
            .end()
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Batch processing completed, aggregating results")
            .aggregate(constant(true), (oldExchange, newExchange) -> {
                @SuppressWarnings("unchecked")
                List<DocumentRecord> records = oldExchange == null ?
                    newExchange.getIn().getBody(List.class) :
                    oldExchange.getIn().getBody(List.class);
                log.info("Session ${header.sessionId} - Aggregated {} records", records.size());
                return newExchange;
            })
            .completionSize(header("CamelSplitSize"))
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Aggregation completed")
            .end();

        // Key rotation sub-route
        from("direct:key-rotation")
            .routeId("key-rotation")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                log.info("Session ${header.sessionId} - Starting key rotation for file: {}", record.getFileId());
                var keyResult = keyRotationService.getNextKey();
                log.info("Session ${header.sessionId} - Key rotation service result: success={}, key={}",
                    keyResult.isSuccess(), keyResult.getSelectedKey() != null ? "[REDACTED]" : "null");
                if (keyResult.isSuccess()) {
                    record.setSelectedKey(keyResult.getSelectedKey());
                    log.info("Session ${header.sessionId} - Successfully set API key for record: {}", record.getFileId());
                } else {
                    record.addError("Key Rotation", keyResult.getError());
                    log.warn("Session ${header.sessionId} - Key rotation failed for file {}: {}", record.getFileId(), keyResult.getError());
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
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - About to fetch document for OCR")
            .to("direct:fetch-document")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Document fetched, fetching OCR prompt")
            .to("direct:fetch-ocr-prompt")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - OCR prompt fetched, performing OCR")
            .to("direct:perform-ocr")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - OCR completed, exporting results")
            .to("direct:export-ocr")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - OCR results exported, logging stage completion")
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
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - About to fetch translation prompt")
            .to("direct:fetch-translation-prompt")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Translation prompt fetched, performing translation")
            .to("direct:perform-translation")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Translation completed, exporting results")
            .to("direct:export-translation")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Translation results exported, logging stage completion")
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
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - About to perform structuring")
            .to("direct:perform-structuring")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Structuring completed, logging stage completion")
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
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - About to perform persistence")
            .to("direct:perform-persistence")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Persistence completed, logging stage completion")
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
                log.info("Session ${header.sessionId} - Preparing to write {} records to CSV: {}", records.size(), csvPath);

                // Set final status for each record
                records.forEach(record -> {
                    if (record.getAllStagesSuccess() && !record.hasErrors()) {
                        record.setStatus("processed");
                        log.debug("Session ${header.sessionId} - Record {} marked as processed", record.getFileId());
                    } else {
                        record.setStatus("failed");
                        log.warn("Session ${header.sessionId} - Record {} marked as failed", record.getFileId());
                    }
                });

                exchange.getIn().setBody(records);
                exchange.getIn().setHeader("filePath", csvPath);
                log.info("Session ${header.sessionId} - Final status set for all records");
            })
            .setHeader("operation", constant("write"))
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Calling CSV processor to write results")
            .process(csvProcessor)
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Results written successfully")
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