package com.web.project.storage.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StorageSpaceQueryDTO {
    @NotNull @Min(1)
    private Integer page = 1;
    @NotNull @Min(1) @Max(100)
    private Integer pageSize = 15;
}
