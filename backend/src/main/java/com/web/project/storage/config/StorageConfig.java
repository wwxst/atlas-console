package com.web.project.storage.config;

import com.web.project.storage.service.CredentialCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({StorageProperties.class, FileProperties.class})
public class StorageConfig {
    @Bean
    CredentialCipher credentialCipher(StorageProperties properties) {
        return new CredentialCipher(properties.encryptionKey());
    }
}
