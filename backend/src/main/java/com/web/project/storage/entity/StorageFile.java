package com.web.project.storage.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class StorageFile {
    private Long id;
    private String provider;
    private Long spaceId;
    private String storageKey;
    private String contentType;
    private long sizeBytes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
