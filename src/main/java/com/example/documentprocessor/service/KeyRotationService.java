package com.example.documentprocessor.service;

import com.example.documentprocessor.model.ProcessingResult;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeyRotationService {

    private final CsvMapper csvMapper;

    private final boolean localTesting = true;
    private final String localKeysCsvPath = "./test_data/gemini_keys.csv";
    private final String keysCsvFileId = "";

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

        if (localTesting) {
            loadKeysFromLocalFile();
        } else {
            loadKeysFromGoogleDrive();
        }
    }

    private void loadKeysFromLocalFile() throws IOException {
        File csvFile = new File(localKeysCsvPath);
        if (!csvFile.exists()) {
            throw new IOException("Keys CSV file not found: " + localKeysCsvPath);
        }

        CsvSchema schema = CsvSchema.emptySchema().withHeader();
        MappingIterator<Map<String, String>> iterator =
            csvMapper.readerFor(Map.class).with(schema).readValues(csvFile);

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
        // TODO: Implement Google Drive CSV reading
        // For now, this would need to download the CSV from Google Drive
        // and parse it similar to the local file approach
        log.warn("Google Drive key loading not implemented yet");
    }

    public int getKeyCount() {
        return availableKeys.size();
    }

    public Map<String, Integer> getKeyUsageStats() {
        return new ConcurrentHashMap<>(keyUsageCount);
    }
}