package com.web.project.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置系统用户密码参数。
 */
public record ResetSysUserPasswordDTO(
        @NotBlank(message = "请输入新密码")
        @Size(min = 8, max = 24, message = "登录密码需为8到24个字符")
        String password
) {
    @Override
    public String toString() {
        return "ResetSysUserPasswordDTO[password=redacted]";
    }
}
