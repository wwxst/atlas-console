package com.web.project.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 新增系统用户参数。
 */
public record CreateSysUserDTO(
        @NotBlank(message = "请输入登录账号")
        @Size(max = 20, message = "登录账号不能超过20个字符")
        String username,

        @NotBlank(message = "请输入登录密码")
        @Size(min = 8, max = 24, message = "登录密码需为8到24个字符")
        String password,

        @NotBlank(message = "请输入系统用户昵称")
        @Size(max = 30, message = "系统用户昵称不能超过30个字符")
        String nickname,

        @NotNull(message = "请选择账号状态")
        @Min(value = 0, message = "账号状态不正确")
        @Max(value = 1, message = "账号状态不正确")
        Integer status
) {
    @Override
    public String toString() {
        return "CreateSysUserDTO[username=" + username + ", password=redacted]";
    }
}
