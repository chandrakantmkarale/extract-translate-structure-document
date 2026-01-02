package com.example.documentprocessor.route;

import com.example.documentprocessor.model.DocumentRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StructureRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // Perform structuring route
        from("direct:perform-structuring")
            .routeId("perform-structuring")
            .log(LoggingLevel.INFO, "Session ${header.sessionId} - Performing structuring")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

                try {
                    // Basic structuring - for now just organize the data
                    var structuredData = new java.util.HashMap<String, Object>();
                    structuredData.put("originalFileId", record.getFileId());
                    structuredData.put("bookName", record.getBookName());
                    structuredData.put("extractedText", record.getExtractedText());
                    structuredData.put("translations", record.getTranslations());
                    structuredData.put("processingMetadata", java.util.Map.of(
                        "startTime", record.getProcessingStartTime(),
                        "endTime", record.getProcessingEndTime(),
                        "success", record.getAllStagesSuccess()
                    ));

                    record.setStructuredData(structuredData);
                    log.info("Structured data for record: {}", record.getFileId());

                } catch (Exception e) {
                    record.addError("Structure", "Failed to structure data: " + e.getMessage());
                }

                exchange.getIn().setBody(record);
            })
            .end();
    }
}