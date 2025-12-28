package com.example.documentprocessor.config;

import org.apache.camel.CamelContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CamelConfig {

    @Bean
    public CamelContext configureCamelContext(CamelContext camelContext) {
        camelContext.getGlobalOptions().put("CamelJacksonEnableTypeConverter", "true");
        camelContext.getGlobalOptions().put("CamelJacksonTypeConverterToPojo", "true");

        return camelContext;
    }
}