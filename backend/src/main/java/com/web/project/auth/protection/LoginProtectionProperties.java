package com.web.project.auth.protection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;
import java.util.List;

/**
 * 登录保护配置。
 *
 * @param maxConcurrentPasswordChecks BCrypt 校验允许同时执行的最大数量
 * @param rateLimitWindow            Redis 登录计数窗口
 * @param maxAttemptsPerIp           单个 IP 在窗口内允许的登录请求数
 * @param maxAttemptsPerAccount      单个账号在窗口内允许的登录请求数
 * @param trustedProxyAddresses      允许读取 X-Forwarded-For 的直接代理地址
 */
@ConfigurationProperties(prefix = "app.auth.login-protection")
public record LoginProtectionProperties(
        int maxConcurrentPasswordChecks,
        Duration rateLimitWindow,
        long maxAttemptsPerIp,
        long maxAttemptsPerAccount,
        List<String> trustedProxyAddresses
) {
    public LoginProtectionProperties(
            int maxConcurrentPasswordChecks,
            Duration rateLimitWindow,
            long maxAttemptsPerIp,
            long maxAttemptsPerAccount
    ) {
        this(maxConcurrentPasswordChecks, rateLimitWindow, maxAttemptsPerIp,
                maxAttemptsPerAccount, List.of());
    }

    @ConstructorBinding
    public LoginProtectionProperties {
        if (maxConcurrentPasswordChecks < 1) {
            throw new IllegalArgumentException("app.auth.login-protection.max-concurrent-password-checks 必须至少为 1");
        }
        if (rateLimitWindow == null || rateLimitWindow.isZero() || rateLimitWindow.isNegative()) {
            throw new IllegalArgumentException("app.auth.login-protection.rate-limit-window 必须为正数");
        }
        if (maxAttemptsPerIp < 1 || maxAttemptsPerAccount < 1) {
            throw new IllegalArgumentException("登录限流次数必须至少为 1");
        }
        trustedProxyAddresses = trustedProxyAddresses == null
                ? List.of()
                : List.copyOf(trustedProxyAddresses);
    }
}
