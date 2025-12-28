package com.example.documentprocessor.documentprocessing.translation;

import com.example.documentprocessor.documentprocessing.config.ApplicationProperties;
import com.example.documentprocessor.shared.model.DocumentRecord;
import com.example.documentprocessor.shared.model.ProcessingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Route for handling document translation processing.
 * Coordinates translation prompt loading, translation execution, and result export.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TranslationRoute extends RouteBuilder {

    private final ApplicationProperties properties;

    @Override
    public void configure() throws Exception {

        // Translation prompt fetching route
        from("direct:fetch-translation-prompt")
            .routeId("fetch-translation-prompt")
            .process(this::fetchTranslationPrompt)
            .end();

        // Translation execution route
        from("direct:perform-translation")
            .routeId("perform-translation")
            .setHeader("operation", constant("translation"))
            .to("bean:translationProcessor")
            .end();

        // Translation results export route
        from("direct:export-translation")
            .routeId("export-translation")
            .process(this::exportTranslationResults)
            .end();
    }

    /**
     * Fetches the translation prompt from the configured source.
     */
    private void fetchTranslationPrompt(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

        try {
            String prompt;
            if (properties.isLocalTesting()) {
                prompt = fetchTranslationPromptFromLocal();
            } else {
                prompt = fetchTranslationPromptFromGoogleDrive();
            }

            exchange.getIn().setHeader("translationPrompt", prompt);

        } catch (Exception e) {
            record.addError("Translation Prompt Fetch",
                "Failed to fetch translation prompt: " + e.getMessage());
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Fetches translation prompt from local file.
     */
    private String fetchTranslationPromptFromLocal() throws Exception {
        Path promptPath = Paths.get(properties.getLocal().getDocsPath(),
            properties.getFiles().getPromptTranslationFilename());

        if (!Files.exists(promptPath)) {
            // Return default prompt if file doesn't exist
            return "Translate the following text to English. Provide only the translated text.";
        }

        return new String(Files.readAllBytes(promptPath));
    }

    /**
     * Fetches translation prompt from Google Drive.
     * TODO: Implement Google Drive integration
     */
    private String fetchTranslationPromptFromGoogleDrive() {
        // TODO: Implement Google Drive prompt fetching
        return "Translate the following text to English. Provide only the translated text.";
    }

    /**
     * Exports translation results to the configured destination.
     */
    private void exportTranslationResults(org.apache.camel.Exchange exchange) {
        DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);

        if (record.getTranslatedText() != null && !record.getTranslatedText().isEmpty()) {
            ProcessingResult result;
            String fileName = "translated_" + record.getFileId().replaceAll("[^a-zA-Z0-9]", "_") + ".txt";

            if (properties.isLocalTesting()) {
                result = exportTranslationToLocal(record, fileName);
            } else {
                result = exportTranslationToGoogleDrive(record, fileName);
            }

            if (!result.isSuccess()) {
                record.addError(result.getStage(), result.getError());
            } else {
                record.setTranslatedFileId(result.getExportedFileId());
            }
        }

        exchange.getIn().setBody(record);
    }

    /**
     * Exports translation results to local filesystem.
     */
    private ProcessingResult exportTranslationToLocal(DocumentRecord record, String fileName) {
        try {
            Path outputPath = Paths.get(properties.getLocal().getOutputPath(), fileName);
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, record.getTranslatedText().getBytes());

            return ProcessingResult.builder()
                .success(true)
                .exportedFileId(outputPath.toString())
                .stage("Translation Export")
                .build();

        } catch (Exception e) {
            return ProcessingResult.failure("Translation Export",
                "Failed to write translation output: " + e.getMessage());
        }
    }

    /**
     * Exports translation results to Google Drive.
     * TODO: Implement Google Drive integration
     */
    private ProcessingResult exportTranslationToGoogleDrive(DocumentRecord record, String fileName) {
        // TODO: Implement Google Drive document creation
        return ProcessingResult.failure("Translation Export",
            "Google Drive translation export not implemented yet");
    }
}