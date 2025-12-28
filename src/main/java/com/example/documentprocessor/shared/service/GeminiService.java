package com.example.documentprocessor.shared.service;

import com.example.documentprocessor.documentprocessing.config.ApplicationProperties;
import com.example.documentprocessor.documentprocessing.keyrotation.KeyRotationService;
import com.example.documentprocessor.shared.model.DocumentRecord;
import com.example.documentprocessor.shared.model.ProcessingResult;
import com.google.cloud.aiplatform.v1beta1.*;
import com.google.protobuf.Value;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ast.Paragraph;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Generic service for interacting with Google's Gemini AI API via Vertex AI.
 * Supports text generation with multiple document attachments and DOCX export.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiService {

    private final ApplicationProperties properties;
    private final KeyRotationService keyRotationService;

    /**
     * Processes a prompt with multiple documents using Gemini API and exports result to DOCX.
     *
     * @param prompt The text prompt for Gemini
     * @param documents List of document records with content
     * @param outputPath Path where the DOCX file should be saved
     * @return ProcessingResult indicating success or failure
     */
    public ProcessingResult processWithDocumentsAndExportToDocx(String prompt, List<DocumentRecord> documents, String outputPath) {
        try {
            String projectId = properties.getGemini().getProjectId();
            String location = properties.getGemini().getLocation();

            // Initialize Vertex AI client
            try (PredictionServiceClient client = PredictionServiceClient.create()) {
                String modelName = String.format("projects/%s/locations/%s/publishers/google/models/gemini-1.5-flash", projectId, location);

                // Build the content
                StringBuilder fullPrompt = new StringBuilder(prompt);

                for (DocumentRecord doc : documents) {
                    if (doc.getExtractedText() != null && !doc.getExtractedText().trim().isEmpty()) {
                        fullPrompt.append("\n\nDocument: ").append(doc.getFileId()).append("\n").append(doc.getExtractedText());
                    }
                }

                // Create the instance
                Map<String, Value> instanceMap = Map.of("content", Value.newBuilder().setStringValue(fullPrompt.toString()).build());

                // Create parameters
                Map<String, Value> parametersMap = Map.of(
                    "temperature", Value.newBuilder().setNumberValue(0.2).build(),
                    "maxOutputTokens", Value.newBuilder().setNumberValue(2048).build()
                );

                // Make the prediction request
                PredictRequest request = PredictRequest.newBuilder()
                    .setEndpoint(modelName)
                    .addInstances(Value.newBuilder().setStructValue(
                        com.google.protobuf.Struct.newBuilder().putAllFields(instanceMap).build()
                    ).build())
                    .setParameters(Value.newBuilder().setStructValue(
                        com.google.protobuf.Struct.newBuilder().putAllFields(parametersMap).build()
                    ).build())
                    .build();

                PredictResponse response = client.predict(request);

                if (response.getPredictionsCount() > 0) {
                    Value prediction = response.getPredictions(0);
                    String geminiResponse = prediction.getStructValue().getFieldsMap().get("content").getStringValue();

                    log.info("Received response from Gemini API, length: {}", geminiResponse.length());

                    // Export to DOCX
                    exportToDocx(geminiResponse, outputPath);

                    return ProcessingResult.builder()
                            .success(true)
                            .processedContent(geminiResponse)
                            .stage("Gemini Processing")
                            .build();
                } else {
                    return ProcessingResult.failure("Gemini Processing", "No response generated from Gemini API");
                }
            }

        } catch (Exception e) {
            log.error("Error processing with Gemini", e);
            return ProcessingResult.failure("Gemini Processing", "Processing failed: " + e.getMessage());
        }
    }

    /**
     * Exports text content to DOCX format.
     * If content is in Markdown format, converts it to DOCX with proper formatting.
     *
     * @param content The text content (potentially in Markdown)
     * @param outputPath Path to save the DOCX file
     * @throws IOException If file writing fails
     */
    private void exportToDocx(String content, String outputPath) throws IOException {
        // Create a new document
        XWPFDocument document = new XWPFDocument();

        // Check if content is Markdown (simple check)
        if (isMarkdownContent(content)) {
            parseMarkdownToDocx(content, document);
        } else {
            // Treat as plain text
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(content);
        }

        // Ensure output directory exists
        Path outputFilePath = Paths.get(outputPath);
        Path parentDir = outputFilePath.getParent();
        if (parentDir != null) {
            parentDir.toFile().mkdirs();
        }

        // Save the document
        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            document.write(out);
        }

        document.close();
        log.info("DOCX file exported successfully to: {}", outputPath);
    }

    /**
     * Simple check to determine if content is in Markdown format.
     */
    private boolean isMarkdownContent(String content) {
        return content.contains("#") || content.contains("**") || content.contains("*") ||
               content.contains("```") || content.contains(">") || content.contains("- ");
    }

    /**
     * Parses Markdown content and converts it to DOCX format.
     */
    private void parseMarkdownToDocx(String markdown, XWPFDocument document) {
        Parser parser = Parser.builder().build();
        Node documentNode = parser.parse(markdown);

        processMarkdownNode(documentNode, document);
    }

    /**
     * Recursively processes Markdown AST nodes and converts them to DOCX elements.
     */
    private void processMarkdownNode(Node node, XWPFDocument document) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading) {
                Heading heading = (Heading) child;
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();

                // Set heading style based on level
                int level = heading.getLevel();
                switch (level) {
                    case 1:
                        paragraph.setStyle("Heading1");
                        run.setBold(true);
                        run.setFontSize(18);
                        break;
                    case 2:
                        paragraph.setStyle("Heading2");
                        run.setBold(true);
                        run.setFontSize(16);
                        break;
                    case 3:
                        paragraph.setStyle("Heading3");
                        run.setBold(true);
                        run.setFontSize(14);
                        break;
                    default:
                        run.setBold(true);
                        run.setFontSize(12);
                }

                run.setText(getTextContent(heading));
            } else if (child instanceof Paragraph) {
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(getTextContent(child));
            } else if (child instanceof Text) {
                // Handle inline formatting would require more complex parsing
                // For now, just add the text
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(child.getChars().toString());
            }

            // Process children recursively
            if (child.hasChildren()) {
                processMarkdownNode(child, document);
            }
        }
    }

    /**
     * Extracts text content from a Markdown node.
     */
    private String getTextContent(Node node) {
        StringBuilder text = new StringBuilder();
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Text) {
                text.append(child.getChars());
            } else {
                text.append(getTextContent(child));
            }
        }
        return text.toString();
    }

    /**
     * Uploads a file to Vertex AI (stub implementation - Vertex AI handles files differently).
     * In practice, files would be uploaded to Google Cloud Storage first.
     */
    public ProcessingResult uploadFile(byte[] fileData, String mimeType, String apiKey) {
        // For now, return a mock file ID
        // In a real implementation, this would upload to GCS and return the GCS URI
        String mockFileId = "mock-file-" + System.currentTimeMillis();
        return ProcessingResult.builder()
            .success(true)
            .fileId(mockFileId)
            .build();
    }

    /**
     * Generates content using Gemini API with optional file reference.
     */
    public ProcessingResult generateContent(String prompt, String fileUri, String apiKey) {
        try {
            String projectId = properties.getGemini().getProjectId();
            String location = properties.getGemini().getLocation();

            // Create AI Platform client
            try (PredictionServiceClient client = PredictionServiceClient.create()) {
                String modelName = String.format("projects/%s/locations/%s/publishers/google/models/gemini-1.5-pro", projectId, location);

                // Build instance with prompt and optional file
                Map<String, Value> instanceMap = new java.util.HashMap<>();
                instanceMap.put("prompt", Value.newBuilder().setStringValue(prompt).build());
                if (fileUri != null) {
                    instanceMap.put("fileUri", Value.newBuilder().setStringValue(fileUri).build());
                }

                Map<String, Value> parametersMap = Map.of(
                    "temperature", Value.newBuilder().setNumberValue(0.2).build(),
                    "maxOutputTokens", Value.newBuilder().setNumberValue(2048).build()
                );

                // Make the prediction request
                PredictRequest request = PredictRequest.newBuilder()
                    .setEndpoint(modelName)
                    .addInstances(Value.newBuilder().setStructValue(
                        com.google.protobuf.Struct.newBuilder().putAllFields(instanceMap).build()
                    ).build())
                    .setParameters(Value.newBuilder().setStructValue(
                        com.google.protobuf.Struct.newBuilder().putAllFields(parametersMap).build()
                    ).build())
                    .build();

                PredictResponse response = client.predict(request);

                if (response.getPredictionsCount() > 0) {
                    Value prediction = response.getPredictions(0);
                    String geminiResponse = prediction.getStructValue().getFieldsMap().get("content").getStringValue();

                    return ProcessingResult.builder()
                        .success(true)
                        .extractedText(geminiResponse)
                        .build();
                } else {
                    return ProcessingResult.builder()
                        .success(false)
                        .error("No predictions received from Gemini API")
                        .build();
                }
            }
        } catch (Exception e) {
            log.error("Error generating content with Gemini API", e);
            return ProcessingResult.builder()
                .success(false)
                .error("Failed to generate content: " + e.getMessage())
                .build();
        }
    }

    /**
     * Cleans up uploaded file (stub implementation).
     */
    public void cleanupFile(String fileId, String apiKey) {
        // In a real implementation, this would delete the file from GCS
        log.info("Cleaning up file: {}", fileId);
    }
}