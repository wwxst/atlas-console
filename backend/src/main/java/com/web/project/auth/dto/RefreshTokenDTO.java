package com.web.project.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Refresh Token 请求参数。
 *
 * 两类账号的 Refresh 接口使用相同结构。
 *
 * @param refreshToken 前端保存的 Refresh Token
 */
public record RefreshTokenDTO(

        @NotBlank(message = "请提供刷新令牌")
        @Size(max = 4096, message = "刷新令牌格式不正确")
        String refreshToken
) {
    @Override
    public String toString() {
        return "RefreshTokenDTO[refreshToken=redacted]";
    }
}
