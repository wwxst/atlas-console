package com.web.project.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record BindStorageSpaceDTO(@NotBlank @Size(max = 63) String bucketName,
                                  @NotBlank @Size(max = 32) String regionId,
                                  @Size(max = 255) String accessDomain,
                                  @NotBlank @Pattern(regexp = "public-read", message = "头像存储空间仅支持公共读（public-read）") String accessPermission,
                                  @Size(max = 128) @Pattern(regexp = "[!-~]+") String accessKeyId,
                                  @Size(max = 128) @Pattern(regexp = "[!-~]+") String accessKeySecret) {
    public BindStorageSpaceDTO(String bucketName, String regionId, String accessDomain, String accessPermission) {
        this(bucketName, regionId, accessDomain, accessPermission, null, null);
    }

    @Override public String toString() {
        return "BindStorageSpaceDTO[bucketName=%s, regionId=%s, accessDomain=%s, accessPermission=%s, credentials=redacted]"
                .formatted(bucketName, regionId, accessDomain, accessPermission);
    }
}
