package com.example.documentprocessor.documentprocessing.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Main application configuration properties.
 * Contains settings for processing behavior and external service configurations.
 */
@Component
@ConfigurationProperties(prefix = "app")
@Data
public class ApplicationProperties {

    /**
     * Whether the application is running in local testing mode.
     * In local mode, files are read from and written to local filesystem.
     * In production mode, Google Drive and Docs APIs are used.
     */
    private boolean localTesting = true;

    /**
     * Directory for local log files.
     */
    private String logLocalDir = "./logs";

    /**
     * Processing configuration settings.
     */
    private Processing processing = new Processing();

    /**
     * Local testing configuration.
     */
    private Local local = new Local();

    /**
     * Google Drive configuration.
     */
    private Google google = new Google();

    /**
     * Email configuration.
     */
    private Email email = new Email();

    /**
     * Gemini API configuration.
     */
    private Gemini gemini = new Gemini();

    /**
     * Workflow configuration.
     */
    private Workflow workflow = new Workflow();

    /**
     * File configuration.
     */
    private Files files = new Files();

    /**
     * Logging configuration.
     */
    private Logging logging = new Logging();

    @Data
    public static class Processing {
        private int batchSize = 1;
        private int maxRetries = 3;
        private long retryDelay = 5000;
    }

    @Data
    public static class Local {
        private String docsPath = "./test_data/input";
        private String outputPath = "./test_data/output";
        private String keysCsvPath = "./test_data/gemini_keys.csv";
        private String errorLogPath = "./test_data/output/error_logs";
    }

    @Data
    public static class Google {
        private Drive drive = new Drive();
        private Docs docs = new Docs();

        @Data
        public static class Drive {
            private String credId = "default";
            private String apiKey = "";
            private String watchFolderId = "";
            private String processingFolderId = "";
            private String processedResultsFolderId = "";
            private String exportOcrFolderId = "";
            private String exportTranslateFolderId = "";
            private String exportPersistFolderId = "";
            private String errorLogFolderId = "";
            private String keysCsvFileId = "";
            private String promptsFolderId = "";
            private String translationCorrectionsFolderId = "";
        }

        @Data
        public static class Docs {
            private String credId = "default";
            private String apiKey = "";
        }
    }

    @Data
    public static class Email {
        private String alertAddress = "admin@example.com";
        private String credId = "default";
        private String apiKey = "";
    }

    @Data
    public static class Gemini {
        private String projectId = "";
        private String location = "us-central1";
        private String apiKey = "";

        public String getVisionApiUrl() {
            return "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent";
        }

        public String getApiUrl() {
            return "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent";
        }
    }

    @Data
    public static class Workflow {
        private Ids ids = new Ids();

        @Data
        public static class Ids {
            private String geminiVision = "gemini-vision";
            private String translate = "translate";
            private String persistence = "persistence";
            private String keyRotation = "key-rotation";
            private String fetchDoc = "fetch-doc";
            private String exportDoc = "export-doc";
            private String errorLog = "error-log";
            private String rateLimit = "rate-limit";
            private String logger = "logger";
            private String structure = "structure";
        }
    }

    @Data
    public static class Files {
        private String promptOcrFilename = "prompt_ocr.txt";
        private String promptTranslationFilename = "prompt_translate.txt";
        private String promptPrefixTranslate = "prompt_translate_";
        private String translationCorrectionsPrefix = "corrections_";
    }

    @Data
    public static class Logging {
        private boolean detailed = false;
        private String level = "INFO";
    }
}