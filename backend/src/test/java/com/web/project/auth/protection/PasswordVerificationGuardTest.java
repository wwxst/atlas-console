package com.web.project.auth.protection;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordVerificationGuardTest {

    private final PasswordEncoder encoder = mock(PasswordEncoder.class);

    @Test
    void successfulVerificationRecordsDurationAndReleasesPermit() {
        when(encoder.matches("password", "hash")).thenReturn(true);
        LoginProtectionMetrics metrics = new LoginProtectionMetrics();
        PasswordVerificationGuard guard = new PasswordVerificationGuard(
                new LoginProtectionProperties(1, Duration.ofSeconds(60), 60, 20), metrics);

        assertTrue(guard.matches(encoder, "password", "hash"));

        assertEquals(0, metrics.currentConcurrent());
        assertEquals(1, metrics.verificationCount());
        assertTrue(metrics.totalVerificationNanos() > 0);
        verify(encoder).matches("password", "hash");
    }

    @Test
    void wrongPasswordIsReturnedAndPermitIsReleased() {
        when(encoder.matches(any(), any())).thenReturn(false);
        LoginProtectionMetrics metrics = new LoginProtectionMetrics();
        PasswordVerificationGuard guard = new PasswordVerificationGuard(
                new LoginProtectionProperties(1, Duration.ofSeconds(60), 60, 20), metrics);

        assertFalse(guard.matches(encoder, "wrong", "hash"));
        assertEquals(0, metrics.currentConcurrent());
        assertEquals(1, metrics.verificationCount());
    }

    @Test
    void encoderExceptionStillReleasesPermit() {
        when(encoder.matches(any(), any())).thenThrow(new IllegalStateException("encoder failure"));
        LoginProtectionMetrics metrics = new LoginProtectionMetrics();
        PasswordVerificationGuard guard = new PasswordVerificationGuard(
                new LoginProtectionProperties(1, Duration.ofSeconds(60), 60, 20), metrics);

        assertThrows(IllegalStateException.class, () -> guard.matches(encoder, "password", "hash"));
        assertEquals(0, metrics.currentConcurrent());
        assertEquals(1, metrics.verificationCount());
    }

    @Test
    void secondConcurrentVerificationIsRejectedWithoutWaiting() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(encoder.matches(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return true;
        });
        LoginProtectionMetrics metrics = new LoginProtectionMetrics();
        PasswordVerificationGuard guard = new PasswordVerificationGuard(
                new LoginProtectionProperties(1, Duration.ofSeconds(60), 60, 20), metrics);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> guard.matches(encoder, "one", "hash"));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            long started = System.nanoTime();
            ExecutionException rejected = assertThrows(ExecutionException.class,
                    () -> executor.submit(() -> guard.matches(encoder, "two", "hash")).get());
            long elapsed = System.nanoTime() - started;
            assertInstanceOf(BusinessException.class, rejected.getCause());
            assertEquals(ErrorCode.LOGIN_RATE_LIMITED,
                    ((BusinessException) rejected.getCause()).getErrorCode());
            assertTrue(elapsed < Duration.ofMillis(100).toNanos());
            release.countDown();
            assertTrue(first.get(5, java.util.concurrent.TimeUnit.SECONDS));
        }

        assertEquals(1, metrics.rejectedCount());
        assertEquals(0, metrics.currentConcurrent());
    }

    @Test
    void failedAcquireDoesNotReleaseAnotherCallersPermit() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(encoder.matches(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return true;
        });
        LoginProtectionMetrics metrics = new LoginProtectionMetrics();
        PasswordVerificationGuard guard = new PasswordVerificationGuard(
                new LoginProtectionProperties(1, Duration.ofSeconds(60), 60, 20), metrics);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> guard.matches(encoder, "one", "hash"));
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,
                    () -> executor.submit(() -> guard.matches(encoder, "two", "hash")).get());
            release.countDown();
            assertTrue(first.get(5, java.util.concurrent.TimeUnit.SECONDS));
        }

        assertEquals(0, metrics.currentConcurrent());
        assertTrue(guard.matches(encoder, "three", "hash"));
        assertEquals(0, metrics.currentConcurrent());
    }

    @Test
    void repeatedFailuresAndExceptionsAlwaysReleaseAllPermits() {
        when(encoder.matches("wrong", "hash")).thenReturn(false);
        when(encoder.matches("broken", "hash")).thenThrow(new IllegalStateException("encoder failure"));
        when(encoder.matches("right", "hash")).thenReturn(true);
        LoginProtectionMetrics metrics = new LoginProtectionMetrics();
        PasswordVerificationGuard guard = new PasswordVerificationGuard(
                new LoginProtectionProperties(1, Duration.ofSeconds(60), 60, 20), metrics);

        assertFalse(guard.matches(encoder, "wrong", "hash"));
        assertThrows(IllegalStateException.class, () -> guard.matches(encoder, "broken", "hash"));
        assertTrue(guard.matches(encoder, "right", "hash"));
        assertEquals(0, metrics.currentConcurrent());
        assertEquals(3, metrics.verificationCount());
    }
}
