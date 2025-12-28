package com.example.documentprocessor.route;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.processor.GeminiProcessor;
import com.example.documentprocessor.service.GoogleDriveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TranslationRoute extends RouteBuilder {

    private final GeminiProcessor geminiProcessor;
    private final GoogleDriveService googleDriveService;

    @Value("${app.local-testing:true}")
    private boolean localTesting;

    @Override
    public void configure() throws Exception {

        // Fetch translation prompt route
        from("direct:fetch-translation-prompt")
            .routeId("fetch-translation-prompt")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                String targetLangs = record.getTargetLangs();

                // For simplicity, process one language at a time
                String[] langs = targetLangs.split(",");
                String targetLang = langs[0].trim(); // TODO: Handle multiple languages

                String prompt;
                if (localTesting) {
                    // Use default prompt
                    prompt = String.format(
                        "Translate the following text to %s. Provide only the translated text without any additional comments or explanations.",
                        targetLang
                    );
                } else {
                    // TODO: Fetch from Google Drive with corrections
                    prompt = String.format(
                        "Translate the following text to %s. Provide only the translated text without any additional comments or explanations.",
                        targetLang
                    );
                }

                exchange.getIn().setHeader("translationPrompt", prompt);
                exchange.getIn().setHeader("targetLanguage", targetLang);
                exchange.getIn().setBody(record);
            })
            .end();

        // Perform translation route
        from("direct:perform-translation")
            .routeId("perform-translation")
            .setHeader("operation", constant("translate"))
            .process(geminiProcessor)
            .end();

        // Export translation results route
        from("direct:export-translation")
            .routeId("export-translation")
            .process(exchange -> {
                DocumentRecord record = exchange.getIn().getBody(DocumentRecord.class);
                String targetLang = exchange.getIn().getHeader("targetLanguage", String.class);

                if (record.getTranslations() != null && record.getTranslations().containsKey(targetLang)) {
                    String translatedText = record.getTranslations().get(targetLang);
                    String fileName = "translated_" + record.getFileId().replaceAll("[^a-zA-Z0-9]", "_") + "_" + targetLang + ".txt";

                    var result = createOutputFile(record, fileName, translatedText);

                    if (!result.isSuccess()) {
                        record.addError(result.getStage(), result.getError());
                    } else {
                        record.setTranslatedFileId(result.getExportedFileId());
                    }
                }

                exchange.getIn().setBody(record);
            })
            .end();
    }

    private com.example.documentprocessor.model.ProcessingResult createOutputFile(
            DocumentRecord record, String fileName, String content) {

        if (localTesting) {
            // Write to local file
            try {
                java.nio.file.Path outputPath = java.nio.file.Paths.get("./test_data/output", fileName);
                java.nio.file.Files.createDirectories(outputPath.getParent());
                java.nio.file.Files.write(outputPath, content.getBytes());
                return com.example.documentprocessor.model.ProcessingResult.builder()
                    .success(true)
                    .exportedFileId(outputPath.toString())
                    .build();
            } catch (Exception e) {
                return com.example.documentprocessor.model.ProcessingResult.builder()
                    .success(false)
                    .error("Failed to write translation output: " + e.getMessage())
                    .stage("Translation Export")
                    .build();
            }
        } else {
            // Upload to Google Drive
            return googleDriveService.createDocument(
                fileName,
                content,
                "dummy-folder-id", // TODO: Get actual folder ID
                "dummy-token" // TODO: Get actual token
            );
        }
    }
}