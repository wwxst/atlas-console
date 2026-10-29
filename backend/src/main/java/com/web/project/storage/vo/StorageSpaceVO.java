package com.web.project.storage.vo;

import com.web.project.storage.entity.StorageSpace;
import java.time.LocalDateTime;

public record StorageSpaceVO(Long id, String bucketName, String regionId, String accessDomain,
                             boolean isDefault, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static StorageSpaceVO of(StorageSpace space, Long defaultId) {
        return new StorageSpaceVO(space.getId(), space.getBucketName(), space.getRegionId(), space.getAccessDomain(),
                space.getId().equals(defaultId), space.getCreatedAt(), space.getUpdatedAt());
    }
}
