package com.example.documentprocessor.service;

import com.example.documentprocessor.model.DocumentRecord;
import com.example.documentprocessor.processor.GeminiProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationProcessingService {

    private final GeminiProcessor geminiProcessor;

    private final String translationPromptFilePath = "./test_data/input/prompt_translation.txt";

    public void processTranslation(DocumentRecord record, String sessionId) {
        try {
            log.info("Session {} - Starting translation processing for file: {}", sessionId, record.getFileId());

            if (record.getExtractedText() == null || record.getExtractedText().trim().isEmpty()) {
                log.warn("Session {} - No extracted text available for translation", sessionId);
                record.addError("Translation", "No extracted text available");
                record.setAllStagesSuccess(false);
                return;
            }

            // Read translation prompt
            String prompt = Files.readString(Paths.get(translationPromptFilePath));

            // Parse target languages
            String[] targetLanguages = record.getTargetLangs().split(",");
            record.setTranslations(new HashMap<>());

            boolean allTranslationsSuccessful = true;

            for (String targetLang : targetLanguages) {
                targetLang = targetLang.trim();
                if (targetLang.isEmpty()) continue;

                log.info("Session {} - Translating to language: {}", sessionId, targetLang);

                var translationResult = geminiProcessor.performTranslation(record, record.getExtractedText(), targetLang, prompt);

                if (translationResult.isSuccess()) {
                    record.getTranslations().put(targetLang, translationResult.getExtractedText());
                    log.info("Session {} - Translation to {} completed successfully", sessionId, targetLang);
                } else {
                    log.error("Session {} - Translation to {} failed: {}", sessionId, targetLang, translationResult.getError());
                    record.addError("Translation", "Failed to translate to " + targetLang + ": " + translationResult.getError());
                    allTranslationsSuccessful = false;
                }
            }

            if (!allTranslationsSuccessful) {
                record.setAllStagesSuccess(false);
            }

        } catch (Exception e) {
            log.error("Session {} - Error during translation processing for file: {}", sessionId, record.getFileId(), e);
            record.addError("Translation", e.getMessage());
            record.setAllStagesSuccess(false);
        }
    }
}