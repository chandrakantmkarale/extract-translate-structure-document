package com.example.documentprocessor.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentRecord {

    // CSV fields
    private String fileId;
    private String targetLangs;
    private String bookName;
    private String status;

    // Processing metadata
    private Integer rowIndex;
    private String originalStatus;
    private LocalDateTime processingStartTime;
    private LocalDateTime processingEndTime;
    private String executionId;
    private String workflowName;
    private String sessionId;
    private String csvPath;

    // Processing state
    private String currentStage;
    private Boolean allStagesSuccess;
    private List<ProcessingError> errors;

    // API keys
    private String selectedKey;

    // Processing results
    private String extractedText;
    private String extractedFileId;
    private Map<String, String> translations;
    private String translatedFileId;
    private String persistedFileId;

    // Structured data
    private Map<String, Object> structuredData;

    public void addError(String stage, String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(ProcessingError.builder()
                .stage(stage)
                .error(error)
                .timestamp(LocalDateTime.now())
                .build());
        this.allStagesSuccess = false;
    }

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProcessingError {
        private String stage;
        private String error;
        private LocalDateTime timestamp;
    }
}