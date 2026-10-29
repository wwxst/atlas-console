package com.web.project.config.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置属性。
 *
 * issuer 和有效期在配置绑定时校验，无效配置使应用启动失败。
 *
 * @param issuer                 Token 签发者
 * @param secret                 Base64 格式的签名密钥
 * @param accessTokenExpiration  Access Token 有效期
 * @param refreshTokenExpiration Refresh Token 有效期
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        Duration accessTokenExpiration,
        Duration refreshTokenExpiration
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("必须配置 app.jwt.issuer");
        }
        requirePositiveDuration(accessTokenExpiration, "app.jwt.access-token-expiration");
        requirePositiveDuration(refreshTokenExpiration, "app.jwt.refresh-token-expiration");
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", secret=redacted]";
    }

    private static void requirePositiveDuration(Duration duration, String property) {
        if (duration == null || duration.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException(property + " 必须至少为 1 秒");
        }
    }
}
