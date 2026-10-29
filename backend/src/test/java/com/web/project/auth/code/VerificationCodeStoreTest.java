package com.web.project.auth.code;

import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用本地真实 Redis 验证验证码、发送锁、每日次数和失败次数的 key 与 TTL。
 */
@SpringBootTest(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.flyway.enabled=false"
})
class VerificationCodeStoreTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired VerificationCodeStore codeStore;
    @Autowired StringRedisTemplate redisTemplate;

    private String target;

    @BeforeEach
    void requireRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.ping();
        } catch (RedisConnectionFailureException exception) {
            Assumptions.assumeTrue(false, "本地 Redis 不可用，跳过真实 Redis 验证码测试");
        }

        target = "test-" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void cleanUp() {
        if (target == null) {
            return;
        }
        Set<String> keys = redisTemplate.keys("auth:code:*" + target + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String today() {
        return ZonedDateTime.now(ZONE).toLocalDate().toString().replace("-", "");
    }

    @Test
    void sendLockBlocksSecondAcquireWithinTtl() {
        assertTrue(codeStore.acquireSendLock(
                VerificationScene.LOGIN, TargetType.PHONE, target
        ));

        // 60 秒内再次获取失败
        assertFalse(codeStore.acquireSendLock(
                VerificationScene.LOGIN, TargetType.PHONE, target
        ));

        Long ttl = redisTemplate.getExpire(
                "auth:code:lock:login:phone:" + target,
                TimeUnit.SECONDS
        );
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 60, "发送锁 TTL 应不超过 60 秒");

        // 释放后可重新获取
        codeStore.releaseSendLock(
                VerificationScene.LOGIN, TargetType.PHONE, target
        );
        assertTrue(codeStore.acquireSendLock(
                VerificationScene.LOGIN, TargetType.PHONE, target
        ));
    }

    @Test
    void dailyCountIncrementsAndExpiresAtEndOfDay() {
        assertEquals(1L, codeStore.incrementDailyCount(TargetType.PHONE, target));
        assertEquals(2L, codeStore.incrementDailyCount(TargetType.PHONE, target));

        String key = "auth:code:daily:phone:" + target + ":" + today();
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 86_400, "每日计数 TTL 应在本自然日内");
    }

    @Test
    void correctCodeIsConsumedOnlyOnce() {
        codeStore.saveCode(VerificationScene.REGISTER, TargetType.PHONE, target, "123456");

        assertTrue(codeStore.consumeCode(
                VerificationScene.REGISTER, TargetType.PHONE, target, "123456"
        ));

        // 同一验证码只能成功一次
        assertFalse(codeStore.consumeCode(
                VerificationScene.REGISTER, TargetType.PHONE, target, "123456"
        ));
    }

    @Test
    void wrongCodeIncrementsFailuresAndDeletesCodeAfterFive() {
        codeStore.saveCode(VerificationScene.LOGIN, TargetType.EMAIL, target, "111111");

        for (int attempt = 0; attempt < 5; attempt++) {
            assertFalse(codeStore.consumeCode(
                    VerificationScene.LOGIN, TargetType.EMAIL, target, "000000"
            ));
        }

        // 达到 5 次失败后验证码被删除，即使输入正确也失败
        assertFalse(codeStore.consumeCode(
                VerificationScene.LOGIN, TargetType.EMAIL, target, "111111"
        ));
    }

    @Test
    void missingCodeFails() {
        assertFalse(codeStore.consumeCode(
                VerificationScene.LOGIN, TargetType.PHONE, target, "123456"
        ));
    }

    @Test
    void replacementCodeStartsWithItsOwnFailureBudget() {
        codeStore.saveCode(VerificationScene.LOGIN, TargetType.PHONE, target, "111111");
        for (int attempt = 0; attempt < 4; attempt++) {
            assertFalse(codeStore.consumeCode(VerificationScene.LOGIN, TargetType.PHONE, target, "000000"));
        }
        codeStore.saveCode(VerificationScene.LOGIN, TargetType.PHONE, target, "222222");
        assertFalse(codeStore.consumeCode(VerificationScene.LOGIN, TargetType.PHONE, target, "000000"));
        assertTrue(codeStore.consumeCode(VerificationScene.LOGIN, TargetType.PHONE, target, "222222"));
    }

    @Test
    void malformedStoredCounterRaisesErrorInsteadOfReturningZero() {
        String key = "auth:code:daily:phone:" + target + ":" + today();
        redisTemplate.opsForValue().set(key, "invalid");
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> codeStore.incrementDailyCount(TargetType.PHONE, target));
    }
}
