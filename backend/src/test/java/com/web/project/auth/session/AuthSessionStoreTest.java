package com.web.project.auth.session;

import com.web.project.auth.model.AccountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用本地真实 Redis 验证会话存取、key 隔离、TTL 和删除幂等。
 *
 * 本机缺少 Redis 时跳过，不把 mock 当作真实 Redis 验收。
 */
@SpringBootTest(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.flyway.enabled=false"
})
class AuthSessionStoreTest {

    @Autowired AuthSessionStore sessionStore;
    @Autowired StringRedisTemplate redisTemplate;

    private final List<String> sessionIds = new ArrayList<>();

    @BeforeEach
    void requireRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.ping();
        } catch (RedisConnectionFailureException exception) {
            Assumptions.assumeTrue(false, "本地 Redis 不可用，跳过真实 Redis 会话测试");
        }
    }

    @AfterEach
    void cleanUp() {
        for (String sessionId : sessionIds) {
            redisTemplate.delete("auth:session:SYS_USER:" + sessionId);
            redisTemplate.delete("auth:session:USER:" + sessionId);
        }
        sessionIds.clear();
    }

    private String newSessionId() {
        String sessionId = "test-" + UUID.randomUUID();
        sessionIds.add(sessionId);
        return sessionId;
    }

    @Test
    void sessionIsStoredWithTtlAndIsolatedByAccountType() {
        String sessionId = newSessionId();

        sessionStore.save(
                AccountType.USER,
                sessionId,
                new AuthSessionData(42L, 3L, null),
                Duration.ofMinutes(5)
        );

        AuthSessionData data = sessionStore.find(AccountType.USER, sessionId);
        assertNotNull(data);
        assertEquals(42L, data.accountId());
        assertEquals(3L, data.passwordVersion());
        assertNull(data.authVersion());

        // 两类账号的 Session key 相互隔离
        assertNull(sessionStore.find(AccountType.SYS_USER, sessionId));

        Long ttl = redisTemplate.getExpire(
                "auth:session:USER:" + sessionId,
                TimeUnit.SECONDS
        );
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 300, "TTL 应为正值且不超过 5 分钟");
    }

    @Test
    void sysUserSessionStoresAuthenticationVersion() {
        String sessionId = newSessionId();

        sessionStore.save(
                AccountType.SYS_USER,
                sessionId,
                new AuthSessionData(7L, null, 4L),
                Duration.ofDays(30)
        );

        AuthSessionData data = sessionStore.find(AccountType.SYS_USER, sessionId);
        assertNotNull(data);
        assertEquals(7L, data.accountId());
        assertNull(data.passwordVersion());
        assertEquals(4L, data.authVersion());
    }

    @Test
    void deleteIsIdempotentAndRemovesSession() {
        String sessionId = newSessionId();
        sessionStore.save(
                AccountType.USER,
                sessionId,
                new AuthSessionData(1L, 0L, null),
                Duration.ofMinutes(5)
        );

        sessionStore.delete(AccountType.USER, sessionId);
        assertNull(sessionStore.find(AccountType.USER, sessionId));

        // 再次删除不抛异常
        assertDoesNotThrow(() -> sessionStore.delete(AccountType.USER, sessionId));
    }

    @Test
    void missingSessionReturnsNull() {
        assertNull(sessionStore.find(AccountType.USER, "missing-" + UUID.randomUUID()));
    }
}
