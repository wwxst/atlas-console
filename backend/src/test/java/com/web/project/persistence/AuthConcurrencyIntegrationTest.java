package com.web.project.persistence;

import com.web.project.auth.code.VerificationCodeStore;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.model.AuthChannel;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用独立 PostgreSQL 库及真实 Redis 验证实际重叠操作，环境不可用时让测试失败。
 */
@EnabledIfEnvironmentVariable(named = "ATLAS_POSTGRES_TEST_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(properties = {
        "spring.datasource.url=${ATLAS_POSTGRES_TEST_URL}",
        "spring.flyway.enabled=true",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AuthConcurrencyIntegrationTest {

    @Autowired
    private VerificationCodeStore codeStore;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private UserAccountMapper userAccountMapper;
    @Autowired
    private AuthChannelService channelService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentCodeConsumptionSucceedsExactlyOnce() throws Exception {
        String target = "race-" + UUID.randomUUID();
        try {
            codeStore.saveCode(VerificationScene.LOGIN, TargetType.EMAIL, target, "123456");
            List<Boolean> results = race(16,
                    ignored -> codeStore.consumeCode(VerificationScene.LOGIN, TargetType.EMAIL, target, "123456"));
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertFalse(codeStore.consumeCode(VerificationScene.LOGIN, TargetType.EMAIL, target, "123456"));
        } finally {
            deleteCodeKeys(target);
        }
    }

    @Test
    void concurrentSendLockHasOneWinnerAndDailyCountDoesNotLoseWrites() throws Exception {
        String target = "race-" + UUID.randomUUID();
        try {
            List<Boolean> locks = race(16,
                    ignored -> codeStore.acquireSendLock(VerificationScene.LOGIN, TargetType.EMAIL, target));
            assertEquals(1, locks.stream().filter(Boolean::booleanValue).count());
            List<Long> counts = race(16, ignored -> codeStore.incrementDailyCount(TargetType.EMAIL, target));
            assertEquals(16, Set.copyOf(counts).size());
            assertTrue(counts.contains(1L));
            assertTrue(counts.contains(16L));
        } finally {
            deleteCodeKeys(target);
        }
    }

    @Test
    void concurrentRegistrationInsertPreservesContactUniqueness() throws Exception {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        try {
            List<Boolean> results = race(8, index -> {
                UserAccount account = new UserAccount();
                account.setId(10_000L + index);
                account.setUserNo(UUID.randomUUID().toString().replace("-", ""));
                account.setEmail(email);
                try {
                    assertEquals(1, userAccountMapper.insert(account));
                    return true;
                } catch (DuplicateKeyException exception) {
                    assertTrue(exception.getMostSpecificCause().getMessage().contains("uk_user_account_email"));
                    return false;
                }
            });
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_account WHERE email = ?", Integer.class, email));
        } finally {
            jdbcTemplate.update("DELETE FROM user_account WHERE email = ?", email);
        }
    }

    @Test
    void concurrentChannelDisablesCannotDisableBothChannels() throws Exception {
        channelService.updateChannel(AuthChannel.PHONE, new AuthChannelUpdateDTO(true, true));
        channelService.updateChannel(AuthChannel.EMAIL, new AuthChannelUpdateDTO(true, true));
        try {
            List<Boolean> results = race(2, index -> {
                try {
                    channelService.updateChannel(index == 0 ? AuthChannel.PHONE : AuthChannel.EMAIL,
                            new AuthChannelUpdateDTO(false, true));
                    return true;
                } catch (BusinessException exception) {
                    assertEquals(ErrorCode.CANNOT_DISABLE_ALL_CHANNELS, exception.getErrorCode());
                    return false;
                }
            });
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
            var channels = channelService.getChannels();
            assertNotEquals(channels.phoneEnabled(), channels.emailEnabled());
        } finally {
            channelService.updateChannel(AuthChannel.PHONE, new AuthChannelUpdateDTO(true, true));
            channelService.updateChannel(AuthChannel.EMAIL, new AuthChannelUpdateDTO(true, true));
        }
    }

    private void deleteCodeKeys(String target) {
        Set<String> keys = redisTemplate.keys("auth:code:*" + target + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private <T> List<T> race(int workers, IntFunction<T> operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        try (ThreadPoolExecutor executor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(workers), Thread.ofPlatform().name("auth-race-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy())) {
            List<Future<T>> futures = new ArrayList<>(workers);
            for (int index = 0; index < workers; index++) {
                int workerIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return operation.apply(workerIndex);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<T> results = new ArrayList<>(workers);
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
        }
    }
}
