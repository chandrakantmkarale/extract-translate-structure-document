package com.example.documentprocessor.documentprocessing.keyrotation;

import com.example.documentprocessor.documentprocessing.config.ApplicationProperties;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service responsible for rotating API keys for external service calls.
 * Supports loading keys from local CSV files or Google Drive.
 * Implements round-robin key rotation to distribute load across available keys.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeyRotationService {

    private final CsvMapper csvMapper;
    private final ApplicationProperties properties;

    // In-memory key rotation state
    private final List<String> availableKeys = new ArrayList<>();
    private final AtomicInteger currentKeyIndex = new AtomicInteger(0);
    private final Map<String, Integer> keyUsageCount = new ConcurrentHashMap<>();

    /**
     * Gets the next available API key using round-robin rotation.
     *
     * @return The selected API key as a String, or null if no keys are available
     */
    public String getNextKey() {
        try {
            // Load keys if not already loaded
            if (availableKeys.isEmpty()) {
                loadKeys();
            }

            // Check if keys are available
            if (availableKeys.isEmpty()) {
                log.error("No API keys available for rotation");
                return null;
            }

            // Select next key using round-robin
            int index = currentKeyIndex.getAndIncrement() % availableKeys.size();
            String selectedKey = availableKeys.get(index);

            // Track usage for monitoring
            keyUsageCount.merge(selectedKey, 1, Integer::sum);

            log.info("Selected API key index: {}, total keys: {}", index, availableKeys.size());

            return selectedKey;

        } catch (Exception e) {
            log.error("Error rotating API key", e);
            return null;
        }
    }

    /**
     * Loads API keys from the configured source.
     * In local testing mode, loads from CSV file.
     * In production mode, would load from Google Drive (not implemented yet).
     *
     * @throws IOException if key loading fails
     */
    private void loadKeys() throws IOException {
        availableKeys.clear();

        if (properties.isLocalTesting()) {
            loadKeysFromLocalFile();
        } else {
            loadKeysFromGoogleDrive();
        }
    }

    /**
     * Loads API keys from a local CSV file.
     * Expected CSV format: single column with header "api_key"
     *
     * @throws IOException if file reading fails
     */
    private void loadKeysFromLocalFile() throws IOException {
        File csvFile = new File(properties.getLocal().getKeysCsvPath());
        if (!csvFile.exists()) {
            throw new IOException("Keys CSV file not found: " + properties.getLocal().getKeysCsvPath());
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

    /**
     * Loads API keys from Google Drive.
     * TODO: Implement Google Drive CSV reading functionality
     */
    private void loadKeysFromGoogleDrive() {
        // TODO: Implement Google Drive CSV download and parsing
        log.warn("Google Drive key loading not implemented yet");
    }

    /**
     * Gets the current count of available keys.
     *
     * @return Number of loaded API keys
     */
    public int getKeyCount() {
        return availableKeys.size();
    }

    /**
     * Gets usage statistics for all keys.
     *
     * @return Map of key to usage count
     */
    public Map<String, Integer> getKeyUsageStats() {
        return new ConcurrentHashMap<>(keyUsageCount);
    }
}