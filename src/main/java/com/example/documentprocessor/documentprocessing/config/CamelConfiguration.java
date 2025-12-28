package com.example.documentprocessor.documentprocessing.config;

import org.apache.camel.CamelContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Apache Camel context.
 * Sets up global options and context-level configurations.
 */
@Configuration
public class CamelConfiguration {

    /**
     * Configures the Camel context with global options.
     *
     * @param camelContext the Camel context to configure
     */
    @Bean
    public CamelContext configureCamelContext(CamelContext camelContext) {
        // Enable Jackson type converter for JSON processing
        camelContext.getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");
        camelContext.getGlobalOptions().put("CamelJacksonTypeConverterToPojo", "true");

        // Configure thread pool for parallel processing
        camelContext.getGlobalOptions().put("CamelThreadPoolRejectedPolicy", "CallerRuns");

        return camelContext;
    }
}