package com.example.documentprocessor.documentprocessing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for REST client components.
 * Provides configured RestTemplate instances for external API calls.
 */
@Configuration
public class RestTemplateConfiguration {

    /**
     * Creates a configured RestTemplate for making HTTP requests.
     * Used by services that interact with external APIs like Gemini and Google Drive.
     *
     * @return Configured RestTemplate instance
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}