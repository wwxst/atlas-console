package com.web.project.auth.dto;

import com.web.project.auth.model.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 普通用户注册参数。
 *
 * @param target     手机号或邮箱
 * @param targetType 联系方式类型
 * @param code       验证码
 * @param password   密码，可选；有值时由 PasswordPolicy 校验
 */
public record UserRegisterDTO(

        @NotBlank(message = "请输入手机号或邮箱")
        @Size(max = 100, message = "手机号或邮箱格式不正确")
        String target,

        @NotNull(message = "请选择联系方式类型")
        TargetType targetType,

        @NotBlank(message = "请输入验证码")
        @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
        String code,

        String password
) {
    @Override
    public String toString() {
        return "UserRegisterDTO[target=redacted, code=redacted, password=redacted]";
    }
}
