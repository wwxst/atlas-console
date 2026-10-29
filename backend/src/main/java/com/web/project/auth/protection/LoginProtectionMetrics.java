package com.web.project.auth.protection;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 登录保护指标；只记录聚合数值，不记录账号、IP、密码或 Token。
 */
@Component
public class LoginProtectionMetrics {

    private final AtomicInteger currentConcurrent = new AtomicInteger();
    private final AtomicLong rejectedCount = new AtomicLong();
    private final AtomicLong redisRateLimitedCount = new AtomicLong();
    private final AtomicLong verificationCount = new AtomicLong();
    private final AtomicLong totalVerificationNanos = new AtomicLong();

    public void onEnter() {
        currentConcurrent.incrementAndGet();
    }

    public void onExit(long elapsedNanos) {
        currentConcurrent.decrementAndGet();
        verificationCount.incrementAndGet();
        totalVerificationNanos.addAndGet(elapsedNanos);
    }

    public void onRejected() {
        rejectedCount.incrementAndGet();
    }

    public void onRedisRateLimited() {
        redisRateLimitedCount.incrementAndGet();
    }

    public int currentConcurrent() {
        return currentConcurrent.get();
    }

    public long rejectedCount() {
        return rejectedCount.get();
    }

    public long redisRateLimitedCount() {
        return redisRateLimitedCount.get();
    }

    public long verificationCount() {
        return verificationCount.get();
    }

    public long totalVerificationNanos() {
        return totalVerificationNanos.get();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                currentConcurrent(),
                rejectedCount(),
                redisRateLimitedCount(),
                verificationCount(),
                totalVerificationNanos()
        );
    }

    public record Snapshot(
            int currentConcurrent,
            long rejectedCount,
            long redisRateLimitedCount,
            long verificationCount,
            long totalVerificationNanos
    ) {
    }
}
