package com.web.project.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record StorageCredentialsDTO(@NotBlank @Size(max = 128) @Pattern(regexp = "[!-~]+") String accessKeyId,
                                    @NotBlank @Size(max = 128) @Pattern(regexp = "[!-~]+") String accessKeySecret) {
    @Override public String toString() { return "StorageCredentialsDTO[redacted]"; }
}
