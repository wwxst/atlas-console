package com.web.project.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.storage")
public record StorageProperties(String encryptionKey) {
    @Override
    public String toString() { return "StorageProperties[encryptionKey=redacted]"; }
}
