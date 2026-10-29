package com.web.project.storage.dto;

import jakarta.validation.constraints.Size;

public record StorageDomainDTO(@Size(max = 255) String accessDomain) {}
