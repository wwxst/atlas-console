package com.web.project.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 普通用户启停用参数。 */
public record UserAccountStatusUpdateDTO(
        @NotNull(message = "用户状态不能为空")
        @Min(value = 0, message = "用户状态不正确")
        @Max(value = 1, message = "用户状态不正确")
        Integer status
) {
}
