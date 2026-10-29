package com.web.project.auth.vo;

/**
 * 双 Token 响应。
 *
 * 两类账号登录成功和普通用户注册成功后统一返回该结构。
 *
 * @param accessToken  Access Token，用于调用业务接口
 * @param refreshToken Refresh Token，用于换取新的 Access Token
 */
public record TokenPairVO(
        String accessToken,
        String refreshToken
) {
    @Override
    public String toString() {
        return "TokenPairVO[accessToken=redacted, refreshToken=redacted]";
    }
}
