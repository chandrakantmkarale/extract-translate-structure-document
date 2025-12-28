package com.example.documentprocessor.documentprocessing.orchestrator;

import com.example.documentprocessor.documentprocessing.config.ApplicationProperties;
import com.example.documentprocessor.documentprocessing.keyrotation.KeyRotationService;
import com.example.documentprocessor.shared.model.DocumentRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Main orchestrator route that coordinates the entire document processing workflow.
 * Handles CSV ingestion, parallel processing, error handling, and result aggregation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrchestratorRoute extends RouteBuilder {

    private final ApplicationProperties properties;
    private final KeyRotationService keyRotationService;

    @Override
    public void configure() throws Exception {

        // Main orchestrator REST endpoint
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
                String csvPath = request.getFileId(); // Using fileId as csvPath
                exchange.getIn().setHeader("csvPath", csvPath);
                log.info("Starting document processing for CSV: {}", csvPath);
            })
            .to("direct:read-csv")
            .to("direct:validate-csv")
            .to("direct:process-batch")
            .to("direct:write-results")
            .marshal().json(JsonLibrary.Jackson);

        // CSV reading route
        from("direct:read-csv")
            .routeId("read-csv")
            .setHeader("operation", constant("read"))
            .to("bean:csvProcessor")
            .end();

        // CSV validation route
        from("direct:validate-csv")
            .routeId("validate-csv")
            .setHeader("operation", constant("validate"))
            .to("bean:csvProcessor")
            .end();

        // Batch processing route with parallel execution
        from("direct:process-batch")
            .routeId("process-batch")
            .split(body()).parallelProcessing()
                .process(this::initializeRecord)
                .to("direct:log-processing-start")
                .to("direct:key-rotation")
                .choice()
                    .when(this::isKeyRotationSuccessful)
                        .to("direct:ocr-processing")
                        .choice()
                            .when(this::isProcessingSuccessful)
                                .to("direct:translate-processing")
                                .choice()
                                    .when(this::isProcessingSuccessful)
                                        .to("direct:persistence-processing")
                                    .endChoice()
                                .endChoice()
                            .endChoice()
                        .endChoice()
                    .endChoice()
                .end()
                .to("direct:log-processing-complete")
                .to("direct:persist-error-log")
            .end()
            .aggregate(constant(true), this::aggregateResults)
            .completionSize(header("CamelSplitSize"))
            .to("direct:persist-batch-summary")
            .end();

        // Key rotation sub-route
        from("direct:key-rotation")
            .routeId("key-rotation")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                String selectedKey = keyRotationService.getNextKey();
                if (selectedKey != null && !selectedKey.isEmpty()) {
                    record.setSelectedKey(selectedKey);
                    log.debug("Selected API key for record: {}", record.getFileId());
                } else {
                    record.addError("Key Rotation", "No API key available");
                }
                exchange.getIn().setBody(record);
            })
            .setHeader("stage", constant("Key Rotation"))
            .to("direct:log-processing-progress")
            .process(this::updateProcessingSuccess)
            .end();

        // OCR processing sub-route
        from("direct:ocr-processing")
            .routeId("ocr-processing")
            .process(this::setOcrStage)
            .to("direct:fetch-document")
            .to("direct:fetch-ocr-prompt")
            .to("direct:perform-ocr")
            .to("direct:export-ocr")
            .setHeader("stage", constant("OCR"))
            .to("direct:log-processing-progress")
            .process(this::updateProcessingSuccess)
            .end();

        // Translation processing sub-route
        from("direct:translate-processing")
            .routeId("translate-processing")
            .process(this::setTranslationStage)
            .to("direct:fetch-translation-prompt")
            .to("direct:perform-translation")
            .to("direct:export-translation")
            .setHeader("stage", constant("Translation"))
            .to("direct:log-processing-progress")
            .process(this::updateProcessingSuccess)
            .end();

        // Persistence processing sub-route
        from("direct:persistence-processing")
            .routeId("persistence-processing")
            .process(this::setPersistenceStage)
            .to("direct:persist-results")
            .setHeader("stage", constant("Persistence"))
            .to("direct:log-processing-progress")
            .process(this::updateProcessingSuccess)
            .end();

        // Result writing route
        from("direct:write-results")
            .routeId("write-results")
            .process(this::prepareFinalResults)
            .setHeader("operation", constant("write"))
            .to("bean:csvProcessor")
            .end();
    }

    /**
     * Initializes a document record with processing metadata.
     */
    private void initializeRecord(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        record.setExecutionId(exchange.getExchangeId());
        record.setWorkflowName("Orchestrator");
        record.setCurrentStage("Key Rotation");
        record.setAllStagesSuccess(true);
        record.setProcessingStartTime(LocalDateTime.now());
        exchange.getIn().setBody(record);
    }

    /**
     * Sets the current stage to OCR processing.
     */
    private void setOcrStage(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        record.setCurrentStage("OCR");
        exchange.getIn().setBody(record);
    }

    /**
     * Sets the current stage to Translation processing.
     */
    private void setTranslationStage(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        record.setCurrentStage("Translation");
        exchange.getIn().setBody(record);
    }

    /**
     * Sets the current stage to Persistence processing.
     */
    private void setPersistenceStage(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        record.setCurrentStage("Persistence");
        exchange.getIn().setBody(record);
    }

    /**
     * Updates processing success based on current record state.
     */
    private void updateProcessingSuccess(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        // If there are errors in the current stage, mark as not successful
        boolean hasErrorsInCurrentStage = record.getErrors().stream()
            .anyMatch(error -> error.getStage().equals(record.getCurrentStage()));
        if (hasErrorsInCurrentStage) {
            record.setAllStagesSuccess(false);
        }
        exchange.getIn().setBody(record);
    }

    /**
     * Checks if key rotation was successful.
     */
    private boolean isKeyRotationSuccessful(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        return record.getAllStagesSuccess() && record.getSelectedKey() != null;
    }

    /**
     * Checks if current processing stage was successful.
     */
    private boolean isProcessingSuccessful(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        return record.getAllStagesSuccess();
    }

    /**
     * Aggregates processing results from parallel executions.
     */
    private org.apache.camel.Exchange aggregateResults(org.apache.camel.Exchange oldExchange,
                                                      org.apache.camel.Exchange newExchange) {
        return newExchange; // Simple aggregation - just return the latest
    }

    /**
     * Prepares final results for CSV writing.
     */
    private void prepareFinalResults(org.apache.camel.Exchange exchange) {
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
            // Set end time if not already set
            if (record.getProcessingEndTime() == null) {
                record.setProcessingEndTime(LocalDateTime.now());
            }
        });

        exchange.getIn().setBody(records);
        exchange.getIn().setHeader("filePath", csvPath);
    }
}