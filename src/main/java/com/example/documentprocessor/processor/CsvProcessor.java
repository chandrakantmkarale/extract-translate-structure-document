package com.example.documentprocessor.processor;

import com.example.documentprocessor.model.DocumentRecord;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CsvProcessor implements Processor {

    private final CsvMapper csvMapper;

    @Override
    public void process(Exchange exchange) throws Exception {
        String operation = exchange.getIn().getHeader("operation", String.class);
        log.info("Processing CSV operation: {}", operation);

        switch (operation) {
            case "read":
                readCsv(exchange);
                break;
            case "write":
                writeCsv(exchange);
                break;
            case "validate":
                validateCsv(exchange);
                break;
            default:
                throw new IllegalArgumentException("Unknown CSV operation: " + operation);
        }
    }

    private void readCsv(Exchange exchange) throws IOException {
        String filePath = exchange.getIn().getBody(String.class);
        File csvFile = new File(filePath);

        if (!csvFile.exists()) {
            throw new IOException("CSV file not found: " + filePath);
        }

        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        MappingIterator<Map<String, String>> iterator =
            csvMapper.readerFor(Map.class).with(schema).readValues(csvFile);

        List<DocumentRecord> records = new ArrayList<>();
        int rowIndex = 0;

        while (iterator.hasNext()) {
            Map<String, String> row = iterator.next();

            DocumentRecord record = DocumentRecord.builder()
                .fileId(row.get("fileId"))
                .targetLangs(row.get("targetLangs"))
                .bookName(row.get("bookName"))
                .status(row.get("status"))
                .rowIndex(rowIndex)
                .originalStatus(row.get("status"))
                .processingStartTime(LocalDateTime.now())
                .currentStage("Initialized")
                .allStagesSuccess(true)
                .errors(new ArrayList<>())
                .build();

            log.info("Read CSV record {}: fileId={}, targetLangs={}, bookName={}, status={}",
                rowIndex, record.getFileId(), record.getTargetLangs(), record.getBookName(), record.getStatus());

            records.add(record);
            rowIndex++;
        }

        exchange.getIn().setBody(records);
        log.info("Read {} records from CSV file: {}", records.size(), filePath);
    }

    private void writeCsv(Exchange exchange) throws IOException {
        @SuppressWarnings("unchecked")
        List<DocumentRecord> records = exchange.getIn().getBody(List.class);
        String filePath = exchange.getIn().getHeader("filePath", String.class);

        if (records == null || records.isEmpty()) {
            log.warn("No records to write to CSV");
            return;
        }

        // Create CSV content
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("fileId,targetLangs,bookName,status,errorMessage\n");

        for (DocumentRecord record : records) {
            csvContent.append(escapeCsvField(record.getFileId())).append(",");
            csvContent.append(escapeCsvField(record.getTargetLangs())).append(",");
            csvContent.append(escapeCsvField(record.getBookName())).append(",");
            csvContent.append(escapeCsvField(record.getStatus())).append(",");

            String errorMessage = record.hasErrors() ?
                record.getErrors().stream()
                    .map(error -> error.getStage() + ": " + error.getError())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("") : "";
            csvContent.append(escapeCsvField(errorMessage)).append("\n");
        }

        // Write to file
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write(csvContent.toString());
        }

        log.info("Wrote {} records to CSV file: {}", records.size(), filePath);
    }

    private void validateCsv(Exchange exchange) throws Exception {
        @SuppressWarnings("unchecked")
        List<DocumentRecord> records = exchange.getIn().getBody(List.class);

        if (records == null || records.isEmpty()) {
            throw new Exception("CSV file is empty or could not be read");
        }

        // Validate required columns
        DocumentRecord firstRecord = records.get(0);
        if (firstRecord.getFileId() == null || firstRecord.getFileId().trim().isEmpty()) {
            throw new Exception("Required column missing or empty: fileId");
        }
        if (firstRecord.getTargetLangs() == null || firstRecord.getTargetLangs().trim().isEmpty()) {
            throw new Exception("Required column missing or empty: targetLangs");
        }

        log.info("CSV validation successful. Records: {}", records.size());
    }

    private String escapeCsvField(String field) {
        if (field == null) return "";

        // If field contains comma, quote, or newline, wrap in quotes and escape quotes
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }

        return field;
    }
}