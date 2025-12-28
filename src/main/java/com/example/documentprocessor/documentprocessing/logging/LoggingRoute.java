package com.example.documentprocessor.documentprocessing.logging;

import com.example.documentprocessor.documentprocessing.config.ApplicationProperties;
import com.example.documentprocessor.shared.model.DocumentRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Route for handling comprehensive logging operations.
 * Manages structured logging of processing activities and performance metrics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoggingRoute extends RouteBuilder {

    private final ApplicationProperties properties;

    @Override
    public void configure() throws Exception {

        // Processing start logging route
        from("direct:log-processing-start")
            .routeId("log-processing-start")
            .process(this::logProcessingStart)
            .end();

        // Processing progress logging route
        from("direct:log-processing-progress")
            .routeId("log-processing-progress")
            .process(this::logProcessingProgress)
            .end();

        // Processing completion logging route
        from("direct:log-processing-complete")
            .routeId("log-processing-complete")
            .process(this::logProcessingComplete)
            .end();

        // Error logging route
        from("direct:log-error")
            .routeId("log-error")
            .process(this::logError)
            .end();

        // Performance metrics logging route
        from("direct:log-performance-metrics")
            .routeId("log-performance-metrics")
            .process(this::logPerformanceMetrics)
            .end();
    }

    /**
     * Logs the start of document processing.
     */
    private void logProcessingStart(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

        log.info("Starting document processing for file: {} at {}",
            record.getFileId(), record.getProcessingStartTime());

        if (properties.getLogging().isDetailed()) {
            log.debug("Processing configuration - Local testing: {}, Profile: {}",
                properties.isLocalTesting(),
                properties.isLocalTesting() ? "dev" : "prod");
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Logs processing progress at different stages.
     */
    private void logProcessingProgress(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        String stage = exchange.getIn().getHeader("stage", String.class);

        if (properties.getLogging().isDetailed()) {
            log.info("Processing stage '{}' completed for file: {}", stage, record.getFileId());

            // Log specific metrics based on stage
            switch (stage) {
                case "OCR" -> {
                    if (record.getExtractedText() != null) {
                        log.debug("OCR completed - extracted {} characters",
                            record.getExtractedText().length());
                    }
                }
                case "Translation" -> {
                    if (record.getTranslatedText() != null) {
                        log.debug("Translation completed - translated {} characters",
                            record.getTranslatedText().length());
                    }
                }
                case "Persistence" -> {
                    log.debug("Results persisted for file: {}", record.getFileId());
                }
            }
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Logs the completion of document processing.
     */
    private void logProcessingComplete(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

        if (record.hasErrors()) {
            log.warn("Document processing completed with {} errors for file: {}",
                record.getErrors().size(), record.getFileId());

            if (properties.getLogging().isDetailed()) {
                record.getErrors().forEach(error ->
                    log.warn("Error in stage '{}': {}", error.getStage(), error.getError()));
            }
        } else {
            log.info("Document processing completed successfully for file: {}", record.getFileId());
        }

        // Log final status summary
        logFinalStatus(record);

        exchange.getIn().setBody(record);
    }

    /**
     * Logs processing errors.
     */
    private void logError(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        Exception exception = exchange.getIn().getHeader("CamelExceptionCaught", Exception.class);

        if (exception != null) {
            log.error("Exception occurred during processing of file {}: {}",
                record.getFileId(), exception.getMessage(), exception);
        } else {
            log.error("Error occurred during processing of file: {}", record.getFileId());
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Logs performance metrics for the processing operation.
     */
    private void logPerformanceMetrics(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
        Long startTime = exchange.getIn().getHeader("startTime", Long.class);
        Long endTime = exchange.getIn().getHeader("endTime", Long.class);

        if (startTime != null && endTime != null) {
            long duration = endTime - startTime;
            log.info("Processing duration for file {}: {} ms", record.getFileId(), duration);

            if (properties.getLogging().isDetailed()) {
                log.debug("Performance metrics - File: {}, Duration: {} ms, Errors: {}",
                    record.getFileId(), duration, record.getErrors().size());
            }
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Logs the final status summary for a document.
     */
    private void logFinalStatus(DocumentRecord record) {
        StringBuilder status = new StringBuilder();
        status.append("Final Status - File: ").append(record.getFileId());
        status.append(", Errors: ").append(record.getErrors().size());

        if (record.getExtractedText() != null) {
            status.append(", OCR: ").append(record.getExtractedText().length()).append(" chars");
        }

        if (record.getTranslatedText() != null) {
            status.append(", Translation: ").append(record.getTranslatedText().length()).append(" chars");
        }

        if (record.hasErrors()) {
            log.warn(status.toString());
        } else {
            log.info(status.toString());
        }
    }
}