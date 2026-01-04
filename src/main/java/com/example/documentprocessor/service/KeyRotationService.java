package com.example.documentprocessor.service;

import com.example.documentprocessor.model.ProcessingResult;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class KeyRotationService {

    private final CsvMapper csvMapper;
    private final Optional<GoogleDriveService> googleDriveService;

    @Value("${app.local-testing:true}")
    private boolean localTesting;

    public KeyRotationService(CsvMapper csvMapper, Optional<GoogleDriveService> googleDriveService) {
        this.csvMapper = csvMapper;
        this.googleDriveService = googleDriveService;
    }

    @Value("${app.local-keys-csv-path:./test_data/gemini_keys.csv}")
    private String localKeysCsvPath;

    @Value("${app.google.drive.keys-csv-file-id:}")
    private String keysCsvFileId;

    // In-memory key rotation state
    private final List<String> availableKeys = new ArrayList<>();
    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);
    private final Map<String, Integer> keyUsageCount = new ConcurrentHashMap<>();

    public ProcessingResult getNextKey() {
        try {
            if (availableKeys.isEmpty()) {
                loadKeys();
            }

            if (availableKeys.isEmpty()) {
                return ProcessingResult.builder()
                    .success(false)
                    .error("No API keys available")
                    .stage("Key Rotation")
                    .build();
            }

            // Simple round-robin rotation
            int index = currentKeyIndex.getAndIncrement() % availableKeys.size();
            String selectedKey = availableKeys.get(index);

            // Track usage
            keyUsageCount.merge(selectedKey, 1, Integer::sum);

            log.info("Selected API key index: {}, total keys: {}", index, availableKeys.size());

            return ProcessingResult.builder()
                .success(true)
                .selectedKey(selectedKey)
                .build();
        } catch (Exception e) {
            log.error("Error rotating API key", e);
            return ProcessingResult.builder()
                .success(false)
                .error(e.getMessage())
                .stage("Key Rotation")
                .build();
        }
    }

    private void loadKeys() throws IOException {
        availableKeys.clear();

        //if (localTesting && keysCsvFileId == null || keysCsvFileId.isEmpty()) {
        //    loadKeysFromLocalFile();
        //} else {
            loadKeysFromGoogleDrive();
        //}
    }

    private void loadKeysFromLocalFile() throws IOException {
        File csvFile = new File(localKeysCsvPath);
        if (!csvFile.exists()) {
            throw new IOException("Keys CSV file not found: " + localKeysCsvPath);
        }

        byte[] fileData = Files.readAllBytes(csvFile.toPath());

        // Parse the CSV content with encoding fallback
        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        MappingIterator<Map<String, String>> iterator = null;

        // Try UTF-8 first
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(fileData);
            InputStreamReader reader = new InputStreamReader(bais, StandardCharsets.UTF_8);
            iterator = csvMapper.readerFor(Map.class).with(schema).readValues(reader);
        } catch (Exception e) {
            log.warn("Failed to parse local CSV with UTF-8, trying ISO-8859-1: {}", e.getMessage());
            // Try ISO-8859-1 as fallback
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(fileData);
                InputStreamReader reader = new InputStreamReader(bais, StandardCharsets.ISO_8859_1);
                iterator = csvMapper.readerFor(Map.class).with(schema).readValues(reader);
            } catch (Exception e2) {
                throw new IOException("Failed to parse CSV with both UTF-8 and ISO-8859-1", e2);
            }
        }

        while (iterator.hasNext()) {
            Map<String, String> row = iterator.next();
            String key = row.get("api_key");
            if (key != null && !key.trim().isEmpty()) {
                availableKeys.add(key.trim());
            }
        }

        log.info("Loaded {} keys from local CSV file", availableKeys.size());
    }

    private void loadKeysFromGoogleDrive() {
        if (googleDriveService.isEmpty()) {
            log.warn("Google Drive service not available - skipping key loading from Google Drive");
            return;
        }

        try {
            // Use service account to download the keys CSV from Google Drive
            var downloadResult = googleDriveService.get().downloadFile(keysCsvFileId);
            if (!downloadResult.isSuccess()) {
                log.error("Failed to download keys CSV from Google Drive: {}", downloadResult.getError());
                return;
            }

            byte[] fileData = downloadResult.getFileData();

            // Parse the CSV content with encoding fallback
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            MappingIterator<Map<String, String>> iterator = null;

            // Try UTF-8 first
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(fileData);
                InputStreamReader reader = new InputStreamReader(bais, StandardCharsets.UTF_8);
                iterator = csvMapper.readerFor(Map.class).with(schema).readValues(reader);
            } catch (Exception e) {
                log.warn("Failed to parse CSV with UTF-8, trying ISO-8859-1: {}", e.getMessage());
                // Try ISO-8859-1 as fallback
                try {
                    ByteArrayInputStream bais = new ByteArrayInputStream(fileData);
                    InputStreamReader reader = new InputStreamReader(bais, StandardCharsets.ISO_8859_1);
                    iterator = csvMapper.readerFor(Map.class).with(schema).readValues(reader);
                } catch (Exception e2) {
                    log.error("Failed to parse CSV with both UTF-8 and ISO-8859-1: {}", e2.getMessage());
                    return;
                }
            }

            while (iterator.hasNext()) {
                Map<String, String> row = iterator.next();
                String key = row.get("api_key");
                if (key != null && !key.trim().isEmpty()) {
                    availableKeys.add(key.trim());
                }
            }

            log.info("Loaded {} keys from Google Drive CSV file", availableKeys.size());
        } catch (Exception e) {
            log.error("Error loading keys from Google Drive", e);
        }
    }

    public int getKeyCount() {
        return availableKeys.size();
    }

    public Map<String, Integer> getKeyUsageStats() {
        return new ConcurrentHashMap<>(keyUsageCount);
    }
}