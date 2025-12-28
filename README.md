# Extract-Translate-Structure Document - Apache Camel Implementation

A Java implementation of the multilingual document processing orchestrator using Apache Camel with Spring Boot.

## Overview

This project replicates the n8n workflow functionality using enterprise integration patterns with Apache Camel. It processes CSV files containing document IDs, performs OCR using Google Gemini Vision, translates text to multiple languages, and persists results.

## Architecture

- **Apache Camel**: Enterprise integration framework for routing and mediation
- **Spring Boot**: Application framework
- **Routes**: Modular processing pipelines
- **Processors**: Business logic components
- **Services**: External API integrations (Gemini, Google Drive)

## Key Components

### Routes
- `OrchestratorRoute`: Main orchestration logic
- `OcrRoute`: Document OCR processing
- `TranslationRoute`: Multi-language translation
- `PersistenceRoute`: Data persistence
- `StructureRoute`: Data structuring
- `LoggerRoute`: Centralized logging

### Services
- `GeminiService`: Google Gemini API integration
- `GoogleDriveService`: Google Drive API integration
- `KeyRotationService`: API key management

### Models
- `DocumentRecord`: Processing data container
- `ProcessingResult`: Operation results

## Configuration

The application supports both local testing and production modes:

### Local Testing Mode
```yaml
app:
  local-testing: true
local:
  docs-path: ./test_data/input
  output-path: ./test_data/output
  keys-csv-path: ./test_data/gemini_keys.csv
```

### Production Mode
```yaml
app:
  local-testing: false
google:
  drive:
    cred-id: your-google-drive-cred-id
    watch-folder-id: your-folder-id
```

## API Endpoints

- `POST /api/process`: Start document processing
  ```json
  {
    "fileId": "/path/to/input.csv"
  }
  ```

## Building and Running

### Prerequisites
- Java 17+
- Maven 3.8+

### Build
```bash
mvn clean compile
```

### Run
```bash
mvn spring-boot:run
```

### Test
```bash
curl -X POST http://localhost:8080/api/process \
  -H "Content-Type: application/json" \
  -d '{"fileId": "./test_data/input/documents.csv"}'
```

## Processing Flow

1. **CSV Ingestion**: Read and validate input CSV
2. **Key Rotation**: Select API key for processing
3. **OCR**: Extract text using Gemini Vision
4. **Translation**: Translate to target languages
5. **Structuring**: Organize processed data
6. **Persistence**: Save results
7. **Logging**: Record processing stages

## Configuration Files

- `application.yml`: Main configuration
- Environment variables for credentials and settings

## Development

The project follows standard Spring Boot and Apache Camel conventions:

- Routes in `route/` package
- Processors in `processor/` package
- Services in `service/` package
- Models in `model/` package
- Configuration in `config/` package

## Error Handling

- Comprehensive error tracking per document
- Stage-specific error logging
- Graceful failure handling with status updates
- Centralized logging for monitoring

## Monitoring

- Spring Boot Actuator endpoints
- Camel route metrics
- Structured logging with JSON output
- Processing status tracking