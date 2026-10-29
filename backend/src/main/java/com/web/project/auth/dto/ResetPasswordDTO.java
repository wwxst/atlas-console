package com.web.project.auth.dto;

import com.web.project.auth.model.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 重置密码参数。
 *
 * 忘记密码和登录态修改密码共用该结构；
 * 登录态修改密码时目标必须属于当前用户。
 *
 * @param target      手机号或邮箱
 * @param targetType  联系方式类型
 * @param code        验证码
 * @param newPassword 新密码
 */
public record ResetPasswordDTO(

        @NotBlank(message = "请输入手机号或邮箱")
        @Size(max = 100, message = "手机号或邮箱格式不正确")
        String target,

        @NotNull(message = "请选择联系方式类型")
        TargetType targetType,

        @NotBlank(message = "请输入验证码")
        @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
        String code,

        @NotBlank(message = "请输入新密码")
        String newPassword
) {
    @Override
    public String toString() {
        return "ResetPasswordDTO[target=redacted, code=redacted, newPassword=redacted]";
    }
}
