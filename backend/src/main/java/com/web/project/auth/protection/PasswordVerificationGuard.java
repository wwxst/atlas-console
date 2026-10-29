package com.web.project.auth.protection;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * 对 BCrypt 校验设置有界、非阻塞的并发保护。
 */
@Component
public class PasswordVerificationGuard {

    private final LoginProtectionMetrics metrics;
    private final Semaphore permits;

    public PasswordVerificationGuard(
            LoginProtectionProperties properties,
            LoginProtectionMetrics metrics
    ) {
        this.metrics = metrics;
        this.permits = new Semaphore(properties.maxConcurrentPasswordChecks());
    }

    /**
     * 尝试执行一次密码校验；没有许可时立即返回统一限流错误。
     */
    public boolean matches(PasswordEncoder encoder, CharSequence rawPassword, String encodedPassword) {
        if (!permits.tryAcquire()) {
            metrics.onRejected();
            throw new BusinessException(ErrorCode.LOGIN_RATE_LIMITED);
        }

        long started = System.nanoTime();
        metrics.onEnter();
        try {
            return encoder.matches(rawPassword, encodedPassword);
        } finally {
            metrics.onExit(System.nanoTime() - started);
            permits.release();
        }
    }
}
