package com.web.project.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Internal JSON serialization for provider responses and persisted SMS template variables.
 * The provider adapters use the Jackson 2 API directly and must not depend on MVC mapper auto-configuration.
 */
@Configuration
public class ProviderJsonConfig {

    @Bean
    ObjectMapper providerObjectMapper() {
        return new ObjectMapper();
    }
}
