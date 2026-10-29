package com.web.project.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 系统用户状态修改参数。
 */
public record SysUserStatusUpdateDTO(
        @NotNull(message = "系统用户状态不能为空")
        @Min(value = 0, message = "系统用户状态不正确")
        @Max(value = 1, message = "系统用户状态不正确")
        Integer status
) {
}
