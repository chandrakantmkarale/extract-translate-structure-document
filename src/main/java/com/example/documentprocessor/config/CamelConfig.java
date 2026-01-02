package com.example.documentprocessor.config;

import org.apache.camel.CamelContext;
import org.apache.camel.spring.boot.CamelContextConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelConfig {

    @Bean
    public CamelContextConfiguration camelContextConfiguration() {
        return new CamelContextConfiguration() {
            @Override
            public void beforeApplicationStart(CamelContext camelContext) {
                camelContext.getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");
                camelContext.getGlobalOptions().put("CamelJacksonTypeConverterToPojo", "true");
            }

            @Override
            public void afterApplicationStart(CamelContext camelContext) {
                // No additional configuration needed
            }
        };
    }
}