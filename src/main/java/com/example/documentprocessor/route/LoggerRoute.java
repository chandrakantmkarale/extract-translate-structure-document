package com.example.documentprocessor.route;

import com.example.documentprocessor.model.DocumentRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoggerRoute extends RouteBuilder {

    private final ObjectMapper objectMapper;

    @Value("${app.log-local-dir:./logs}")
    private String logDir;

    @Override
    public void configure() throws Exception {

        // Log stage completion route
        from("direct:log-stage")
            .routeId("log-stage")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Logging stage completion")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                String stage = record.getCurrentStage();

                try {
                    // Create log entry
                    var logEntry = new java.util.HashMap<String, Object>();
                    logEntry.put("timestamp", LocalDateTime.now());
                    logEntry.put("stage", stage);
                    logEntry.put("recordId", record.getFileId());
                    logEntry.put("executionId", record.getExecutionId());
                    logEntry.put("workflowName", record.getWorkflowName());
                    logEntry.put("success", record.getAllStagesSuccess());
                    logEntry.put("errors", record.getErrors());

                    // Write to log file
                    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                    String fileName = stage.replaceAll("\\s+", "_") + "_" + timestamp + ".log";
                    Path logPath = Paths.get(logDir, fileName);

                    Files.createDirectories(logPath.getParent());
                    String logContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(logEntry);
                    Files.write(logPath, logContent.getBytes());

                    log.info("Logged stage completion: {} for record: {}", stage, record.getFileId());

                } catch (Exception e) {
                    log.error("Failed to log stage completion", e);
                }

                exchange.getIn().setBody(record);
            })
            .end();
    }
}