package com.web.project.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.file")
public record FileProperties(String uploadDir) {
    public FileProperties {
        if (uploadDir == null || uploadDir.isBlank()) {
            throw new IllegalArgumentException("必须配置 app.file.upload-dir");
        }
    }
}
