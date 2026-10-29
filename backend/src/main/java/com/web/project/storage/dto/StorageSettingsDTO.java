package com.web.project.storage.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record StorageSettingsDTO(@NotNull @Pattern(regexp = "local|aliyun") String storageType) {}
