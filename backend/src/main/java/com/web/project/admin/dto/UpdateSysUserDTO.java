package com.web.project.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 编辑系统用户参数。
 */
public record UpdateSysUserDTO(
        @NotBlank(message = "请输入登录账号")
        @Size(max = 20, message = "登录账号不能超过20个字符")
        String username,

        @NotBlank(message = "请输入系统用户昵称")
        @Size(max = 30, message = "系统用户昵称不能超过30个字符")
        String nickname
) {
}
