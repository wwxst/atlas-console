package com.web.project.config;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * 独立的 Refresh Token 校验入口。
 *
 * 验证签名、issuer、时间字段和 tokenType=refresh。
 * 与只接受 Access Token 的资源解码器隔离，避免 Refresh Token 被用于访问业务接口。
 */
public class RefreshTokenDecoder {

    private final JwtDecoder delegate;

    RefreshTokenDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    /**
     * 校验并解析 Refresh Token。
     *
     * @param refreshToken 前端提交的 Refresh Token
     * @return 解析后的 JWT
     * @throws org.springframework.security.oauth2.jwt.JwtException 令牌无效或已过期
     */
    public Jwt decode(String refreshToken) {
        return delegate.decode(refreshToken);
    }
}
