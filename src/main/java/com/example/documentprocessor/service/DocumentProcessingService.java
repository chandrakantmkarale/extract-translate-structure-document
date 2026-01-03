package com.example.documentprocessor.service;

import com.example.documentprocessor.model.DocumentRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentProcessingService {

    private final CsvProcessingService csvProcessingService;
    private final KeyRotationService keyRotationService;
    private final OcrProcessingService ocrProcessingService;
    private final TranslationProcessingService translationProcessingService;
    private final StructureProcessingService structureProcessingService;
    private final PersistenceProcessingService persistenceProcessingService;
    private final LoggingService loggingService;

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public DocumentProcessingService(CsvProcessingService csvProcessingService,
                                   KeyRotationService keyRotationService,
                                   OcrProcessingService ocrProcessingService,
                                   TranslationProcessingService translationProcessingService,
                                   StructureProcessingService structureProcessingService,
                                   PersistenceProcessingService persistenceProcessingService,
                                   LoggingService loggingService) {
        this.csvProcessingService = csvProcessingService;
        this.keyRotationService = keyRotationService;
        this.ocrProcessingService = ocrProcessingService;
        this.translationProcessingService = translationProcessingService;
        this.structureProcessingService = structureProcessingService;
        this.persistenceProcessingService = persistenceProcessingService;
        this.loggingService = loggingService;
    }

    public List<DocumentRecord> processDocuments(DocumentRecord request) throws Exception {
        String sessionId = UUID.randomUUID().toString();
        String csvPath = request.getFileId(); // Using fileId as csvPath

        log.info("Starting processing session {} for CSV: {}", sessionId, csvPath);

        // Read CSV
        List<DocumentRecord> records = csvProcessingService.readCsv(csvPath);

        // Validate CSV
        csvProcessingService.validateCsv(records);

        // Process records in parallel
        List<CompletableFuture<DocumentRecord>> futures = records.stream()
            .map(record -> CompletableFuture.supplyAsync(() -> processSingleRecord(record, sessionId), executorService))
            .collect(Collectors.toList());

        // Wait for all to complete
        records = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());

        // Write results
        csvProcessingService.writeCsv(records, csvPath);

        log.info("Completed processing session {} with {} records", sessionId, records.size());
        return records;
    }

    private DocumentRecord processSingleRecord(DocumentRecord record, String sessionId) {
        try {
            log.info("Session {} - Processing record: {}", sessionId, record.getFileId());

            // Initialize record
            record.setExecutionId(UUID.randomUUID().toString());
            record.setWorkflowName("Orchestrator");
            record.setCurrentStage("Key Rotation");
            record.setAllStagesSuccess(true);

            // Key rotation
            var keyResult = keyRotationService.getNextKey();
            if (keyResult.isSuccess()) {
                record.setSelectedKey(keyResult.getSelectedKey());
                record.setCurrentStage("OCR");

                // OCR processing
                ocrProcessingService.processOcr(record, sessionId);
                if (record.getAllStagesSuccess()) {
                    record.setCurrentStage("Translation");

                    // Translation processing
                    translationProcessingService.processTranslation(record, sessionId);
                    if (record.getAllStagesSuccess()) {
                        record.setCurrentStage("Structure");

                        // Structure processing
                        structureProcessingService.processStructure(record, sessionId);
                        if (record.getAllStagesSuccess()) {
                            record.setCurrentStage("Persistence");

                            // Persistence processing
                            persistenceProcessingService.processPersistence(record, sessionId);
                        }
                    }
                }
            } else {
                record.addError("Key Rotation", keyResult.getError());
            }

            // Set final status
            if (record.getAllStagesSuccess() && !record.hasErrors()) {
                record.setStatus("processed");
            } else {
                record.setStatus("failed");
            }

            record.setProcessingEndTime(java.time.LocalDateTime.now());

            // Log completion
            loggingService.logStageCompletion(record, sessionId);

            log.info("Session {} - Completed processing record: {} with status: {}",
                sessionId, record.getFileId(), record.getStatus());
            return record;

        } catch (Exception e) {
            log.error("Session {} - Error processing record: {}", sessionId, record.getFileId(), e);
            record.addError("Processing", e.getMessage());
            record.setStatus("failed");
            record.setAllStagesSuccess(false);
            return record;
        }
    }
}