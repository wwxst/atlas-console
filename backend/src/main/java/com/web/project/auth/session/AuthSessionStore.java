package com.web.project.auth.session;

import com.web.project.auth.model.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

/**
 * 登录会话存储。
 *
 * 统一读写 auth:session:{accountType}:{sessionId}。
 * 两类账号的 key 通过 accountType 段相互隔离。
 */
@Component
@RequiredArgsConstructor
public class AuthSessionStore {

    private static final String KEY_PREFIX = "auth:session:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 写入会话，TTL 为 Refresh Token 有效期。
     */
    public void save(
            AccountType accountType,
            String sessionId,
            AuthSessionData data,
            Duration ttl
    ) {
        redisTemplate.opsForValue().set(
                buildKey(accountType, sessionId),
                objectMapper.writeValueAsString(data),
                ttl
        );
    }

    /**
     * 读取会话。
     *
     * @return 会话数据；不存在时返回 null
     */
    public AuthSessionData find(AccountType accountType, String sessionId) {
        String value = redisTemplate.opsForValue().get(
                buildKey(accountType, sessionId)
        );

        if (value == null) {
            return null;
        }

        return objectMapper.readValue(value, AuthSessionData.class);
    }

    /**
     * 删除会话。
     *
     * key 不存在时同样视为成功，接口保持幂等。
     */
    public void delete(AccountType accountType, String sessionId) {
        redisTemplate.delete(buildKey(accountType, sessionId));
    }

    private String buildKey(AccountType accountType, String sessionId) {
        return KEY_PREFIX + accountType.name() + ":" + sessionId;
    }
}
