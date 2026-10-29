package com.web.project.auth.protection;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 登录 IP 与账号双维度固定窗口限流。
 *
 * Redis 脚本保证计数与首次 TTL 设置原子完成，避免并发请求留下永久计数。
 */
@Component
public class LoginRateLimiter {

    private static final String IP_PREFIX = "auth:login:rate:ip:";
    private static final String ACCOUNT_PREFIX = "auth:login:rate:account:";
    private static final RedisScript<Long> RATE_SCRIPT = RedisScript.of("""
            local ipCount = redis.call('INCR', KEYS[1])
            if ipCount == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            local accountCount = redis.call('INCR', KEYS[2])
            if accountCount == 1 then
                redis.call('EXPIRE', KEYS[2], tonumber(ARGV[1]))
            end
            if ipCount > tonumber(ARGV[2]) or accountCount > tonumber(ARGV[3]) then
                return 0
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final LoginProtectionProperties properties;
    private final LoginProtectionMetrics metrics;

    @Autowired
    public LoginRateLimiter(
            StringRedisTemplate redisTemplate,
            LoginProtectionProperties properties,
            LoginProtectionMetrics metrics
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.metrics = metrics;
    }

    /** 便于不关注指标的纯限流单元测试。 */
    public LoginRateLimiter(StringRedisTemplate redisTemplate, LoginProtectionProperties properties) {
        this(redisTemplate, properties, new LoginProtectionMetrics());
    }

    /**
     * @return true 表示两个维度都未超过窗口上限
     */
    public boolean allow(String ipAddress, String accountKey) {
        Long result = redisTemplate.execute(
                RATE_SCRIPT,
                List.of(
                        IP_PREFIX + digest(ipAddress),
                        ACCOUNT_PREFIX + digest(accountKey)
                ),
                String.valueOf(properties.rateLimitWindow().toSeconds()),
                String.valueOf(properties.maxAttemptsPerIp()),
                String.valueOf(properties.maxAttemptsPerAccount())
        );
        boolean allowed = Objects.requireNonNull(result, "Redis 登录限流操作未返回结果") == 1L;
        if (!allowed) {
            metrics.onRedisRateLimited();
        }
        return allowed;
    }

    private String digest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 不支持 SHA-256", exception);
        }
    }
}
