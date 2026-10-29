package com.web.project.user.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理端直接编辑普通用户资料；空联系方式表示清空，必须保留至少一种登录联系方式。
 */
public record UpdateUserAccountDTO(
        @NotBlank(message = "请输入昵称")
        @Size(max = 50, message = "昵称不能超过50个字符")
        String nickname,
        String phone,
        String email
) {
    public UpdateUserAccountDTO {
        nickname = nickname == null ? null : nickname.trim();
        phone = optionalContact(phone);
        email = optionalContact(email);
    }

    @AssertTrue(message = "至少保留一个手机号或邮箱")
    public boolean isContactPresent() {
        return phone != null || email != null;
    }

    private static String optionalContact(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    public String toString() {
        return "UpdateUserAccountDTO[contacts=redacted]";
    }
}
