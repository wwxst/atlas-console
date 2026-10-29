package com.web.project.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求参数。
 *
 * 接收前端提交的系统用户账号和密码。
 */
public record SysUserLoginDTO(
        @NotBlank(message = "请输入账号")
        @Size(max = 20, message = "账号格式不正确")
        String username,

        // 登录不检查创建密码时的复杂度，只限制输入上限。
        @NotBlank(message = "请输入密码")
        @Size(max = 24, message = "密码格式不正确")
        String password
) {
    @Override
    public String toString() {
        return "SysUserLoginDTO[password=redacted]";
    }
}
