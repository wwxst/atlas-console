package com.web.project.auth.protection;

import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletRequest;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class LoginRateLimiterTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final LoginRateLimiter limiter = new LoginRateLimiter(
            redisTemplate,
            new LoginProtectionProperties(8, Duration.ofSeconds(60), 60, 20)
    );

    @Test
    void allowsRequestWhenIpAndAccountCountersAreUnderLimits() {
        when(redisTemplate.execute(any(), anyList(), eq("60"), eq("60"), eq("20")))
                .thenReturn(1L);

        assertTrue(limiter.allow("127.0.0.1", "USER:EMAIL:person@example.com"));
        verify(redisTemplate).execute(any(), anyList(), eq("60"), eq("60"), eq("20"));
    }

    @Test
    void rejectsRequestWhenEitherRedisCounterExceedsLimit() {
        when(redisTemplate.execute(any(), anyList(), eq("60"), eq("60"), eq("20")))
                .thenReturn(0L);

        assertFalse(limiter.allow("127.0.0.1", "USER:EMAIL:person@example.com"));
    }

    @Test
    void recordsRedisRateLimitRejectionSeparately() {
        LoginProtectionMetrics metrics = new LoginProtectionMetrics();
        LoginRateLimiter measuredLimiter = new LoginRateLimiter(
                redisTemplate,
                new LoginProtectionProperties(8, Duration.ofSeconds(60), 60, 20),
                metrics
        );
        when(redisTemplate.execute(any(), anyList(), eq("60"), eq("60"), eq("20")))
                .thenReturn(0L);

        assertFalse(measuredLimiter.allow("127.0.0.1", "USER:EMAIL:person@example.com"));
        assertEquals(1, metrics.redisRateLimitedCount());
        assertEquals(0, metrics.rejectedCount());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void twoClientsBehindSameNginxHaveDifferentRedisIpCounters() {
        var props = new LoginProtectionProperties(
                8, Duration.ofSeconds(60), 60, 20, List.of("172.30.80.10"));
        var resolver = new ClientIpResolver(props);
        var liveLimiter = new LoginRateLimiter(redisTemplate, props);
        when(redisTemplate.execute(any(), anyList(), eq("60"), eq("60"), eq("20")))
                .thenReturn(1L);

        HttpServletRequest first = mock(HttpServletRequest.class);
        when(first.getRemoteAddr()).thenReturn("172.30.80.10");
        when(first.getHeader("X-Forwarded-For")).thenReturn("198.51.100.10");
        HttpServletRequest second = mock(HttpServletRequest.class);
        when(second.getRemoteAddr()).thenReturn("172.30.80.10");
        when(second.getHeader("X-Forwarded-For")).thenReturn("198.51.100.11");

        assertTrue(liveLimiter.allow(resolver.resolve(first), "USER:EMAIL:first@example.com"));
        assertTrue(liveLimiter.allow(resolver.resolve(second), "USER:EMAIL:second@example.com"));

        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(2)).execute(any(), keys.capture(), eq("60"), eq("60"), eq("20"));
        assertFalse(keys.getAllValues().get(0).getFirst().equals(keys.getAllValues().get(1).getFirst()));
    }

    @Test
    void redisFailurePropagatesInsteadOfBecomingRateLimited() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(redisTemplate).execute(any(), anyList(), eq("60"), eq("60"), eq("20"));

        assertThrows(IllegalStateException.class,
                () -> limiter.allow("127.0.0.1", "USER:EMAIL:person@example.com"));
    }
}
