package com.web.project.auth.dto;

import com.web.project.auth.model.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 普通用户登录参数。
 *
 * password 与 code 必须且只能提供一个，二选一校验在 Service 中完成。
 *
 * @param target     手机号或邮箱
 * @param targetType 联系方式类型
 * @param password   密码，密码登录时提供
 * @param code       验证码，验证码登录时提供
 */
public record UserLoginDTO(

        @NotBlank(message = "请输入手机号或邮箱")
        @Size(max = 100, message = "手机号或邮箱格式不正确")
        String target,

        @NotNull(message = "请选择联系方式类型")
        TargetType targetType,

        @Size(max = 72, message = "密码格式不正确")
        String password,

        @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
        String code
) {
    @Override
    public String toString() {
        return "UserLoginDTO[target=redacted, password=redacted, code=redacted]";
    }
}
