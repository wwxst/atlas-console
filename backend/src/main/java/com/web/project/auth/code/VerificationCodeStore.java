package com.web.project.auth.code;

import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 验证码存储。
 *
 * 集中实现验证码、发送锁、每日次数和失败次数的 Redis key 与 TTL，
 * 业务 Service 不直接拼接 Redis key。
 */
@Component
@RequiredArgsConstructor
public class VerificationCodeStore {

    private static final String CODE_PREFIX = "auth:code:";
    private static final String LOCK_PREFIX = "auth:code:lock:";
    private static final String DAILY_PREFIX = "auth:code:daily:";
    private static final String FAIL_PREFIX = "auth:code:fail:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_LOCK_TTL = Duration.ofSeconds(60);
    private static final long FAIL_TTL_SECONDS = 300L;
    private static final long MAX_FAIL_COUNT = 5L;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    // 计数与首次设置过期时间必须一同完成，避免留下无 TTL 的每日计数。
    private static final RedisScript<Long> DAILY_COUNT_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            return count
            """, Long.class);

    // 新验证码有独立的失败次数，覆盖旧验证码时同时清除旧计数。
    private static final RedisScript<Long> SAVE_CODE_SCRIPT = RedisScript.of("""
            redis.call('SET', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[2]))
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    /**
     * 原子消费验证码：
     * 验证码正确时删除验证码和失败次数并返回 1；
     * 验证码错误时递增失败次数，达到上限后删除验证码并返回 0；
     * 验证码不存在时返回 -1。
     */
    private static final RedisScript<Long> CONSUME_SCRIPT = RedisScript.of("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then
                return -1
            end
            if stored == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('DEL', KEYS[2])
                return 1
            end
            local fails = redis.call('INCR', KEYS[2])
            if fails == 1 then
                redis.call('EXPIRE', KEYS[2], tonumber(ARGV[2]))
            end
            if fails >= tonumber(ARGV[3]) then
                redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 获取发送锁，TTL 60 秒。
     *
     * @return true 表示获取成功，false 表示仍在发送间隔内
     */
    public boolean acquireSendLock(
            VerificationScene scene,
            TargetType targetType,
            String target
    ) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey(scene, targetType, target),
                "1",
                SEND_LOCK_TTL
        );

        return Objects.requireNonNull(acquired, "Redis 发送锁操作未返回结果");
    }

    /**
     * 释放发送锁。
     *
     * 仅在每日发送次数超限时使用，让用户不必额外等待 60 秒。
     */
    public void releaseSendLock(
            VerificationScene scene,
            TargetType targetType,
            String target
    ) {
        redisTemplate.delete(lockKey(scene, targetType, target));
    }

    /**
     * 增加当日发送次数。
     *
     * 首次增加时把 TTL 设置到 Asia/Shanghai 当天结束。
     *
     * @return 增加后的当日发送次数
     */
    public long incrementDailyCount(TargetType targetType, String target) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        String key = DAILY_PREFIX + targetType.key() + ":" + target + ":"
                + now.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        Long count = redisTemplate.execute(
                DAILY_COUNT_SCRIPT,
                List.of(key),
                String.valueOf(ttlUntilEndOfDay(now).toSeconds())
        );
        return Objects.requireNonNull(count, "Redis 每日计数操作未返回结果");
    }

    /**
     * 写入当前验证码，TTL 5 分钟。
     */
    public void saveCode(
            VerificationScene scene,
            TargetType targetType,
            String target,
            String code
    ) {
        Long result = redisTemplate.execute(
                SAVE_CODE_SCRIPT,
                List.of(codeKey(scene, targetType, target), failKey(scene, targetType, target)),
                code,
                String.valueOf(CODE_TTL.toSeconds())
        );
        Objects.requireNonNull(result, "Redis 验证码写入操作未返回结果");
    }

    /**
     * 原子校验并消费验证码。
     *
     * 比较和删除在同一次存储操作中完成，确保同一个验证码只有一个请求成功。
     *
     * @return true 表示验证码正确且已消费
     */
    public boolean consumeCode(
            VerificationScene scene,
            TargetType targetType,
            String target,
            String code
    ) {
        Long result = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(
                        codeKey(scene, targetType, target),
                        failKey(scene, targetType, target)
                ),
                code,
                String.valueOf(FAIL_TTL_SECONDS),
                String.valueOf(MAX_FAIL_COUNT)
        );

        return Objects.requireNonNull(result, "Redis 验证码消费操作未返回结果") == 1L;
    }

    private String codeKey(
            VerificationScene scene,
            TargetType targetType,
            String target
    ) {
        return CODE_PREFIX + scene.key() + ":" + targetType.key() + ":" + target;
    }

    private String lockKey(
            VerificationScene scene,
            TargetType targetType,
            String target
    ) {
        return LOCK_PREFIX + scene.key() + ":" + targetType.key() + ":" + target;
    }

    private String failKey(
            VerificationScene scene,
            TargetType targetType,
            String target
    ) {
        return FAIL_PREFIX + scene.key() + ":" + targetType.key() + ":" + target;
    }

    private Duration ttlUntilEndOfDay(ZonedDateTime now) {
        ZonedDateTime endOfDay = now.toLocalDate()
                .plusDays(1)
                .atStartOfDay(ZONE);

        long seconds = Duration.between(now, endOfDay).getSeconds();

        return Duration.ofSeconds(Math.max(seconds, 1L));
    }
}
