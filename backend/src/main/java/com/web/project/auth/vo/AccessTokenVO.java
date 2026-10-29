package com.web.project.auth.vo;

/**
 * 刷新 Token 后的响应。
 *
 * Refresh Token 在同一个 Session 生命周期内保持不变，因此只返回新的 Access Token。
 *
 * @param accessToken 新的 Access Token
 */
public record AccessTokenVO(
        String accessToken
) {
    @Override
    public String toString() {
        return "AccessTokenVO[accessToken=redacted]";
    }
}
