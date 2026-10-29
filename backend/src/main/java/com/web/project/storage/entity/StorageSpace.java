package com.web.project.storage.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
public class StorageSpace {
    private Long id;
    private String bucketName;
    private String regionId;
    private String accessDomain;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
