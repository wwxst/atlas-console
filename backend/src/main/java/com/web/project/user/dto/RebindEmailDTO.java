package com.web.project.user.dto;

import com.web.project.auth.model.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 换绑邮箱参数。
 *
 * 同时提交旧联系方式和新邮箱的验证码，证明两端联系方式的控制权。
 *
 * @param oldTarget     旧联系方式
 * @param oldTargetType 旧联系方式类型
 * @param oldCode       旧联系方式验证码
 * @param newEmail      新邮箱
 * @param newCode       新邮箱验证码
 */
public record RebindEmailDTO(

        @NotBlank(message = "请输入旧联系方式")
        @Size(max = 100, message = "旧联系方式格式不正确")
        String oldTarget,

        @NotNull(message = "请选择旧联系方式类型")
        TargetType oldTargetType,

        @NotBlank(message = "请输入旧联系方式验证码")
        @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
        String oldCode,

        @NotBlank(message = "请输入新邮箱")
        @Size(max = 100, message = "邮箱格式不正确")
        String newEmail,

        @NotBlank(message = "请输入新邮箱验证码")
        @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
        String newCode
) {
    @Override
    public String toString() {
        return "RebindEmailDTO[contacts=redacted, codes=redacted]";
    }
}
