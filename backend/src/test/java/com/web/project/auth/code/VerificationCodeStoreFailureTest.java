package com.web.project.auth.code;

import com.web.project.auth.model.TargetType;
import com.web.project.auth.model.VerificationScene;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VerificationCodeStoreFailureTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final VerificationCodeStore store = new VerificationCodeStore(redis);

    @Test
    @SuppressWarnings("unchecked")
    void absentSendLockResultIsNotReportedAsRateLimitRejection() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), anyString(), any(java.time.Duration.class))).thenReturn(null);
        assertThrows(NullPointerException.class,
                () -> store.acquireSendLock(VerificationScene.LOGIN, TargetType.PHONE, "test-target"));
    }

    @Test
    void absentDailyCountResultIsNotReportedAsZero() {
        assertThrows(NullPointerException.class,
                () -> store.incrementDailyCount(TargetType.PHONE, "test-target"));
    }

    @Test
    void absentCodeWriteResultDoesNotReturnSuccess() {
        assertThrows(NullPointerException.class,
                () -> store.saveCode(VerificationScene.LOGIN, TargetType.PHONE, "test-target", "123456"));
    }

    @Test
    void absentConsumptionResultIsNotReportedAsWrongCode() {
        assertThrows(NullPointerException.class,
                () -> store.consumeCode(VerificationScene.LOGIN, TargetType.PHONE, "test-target", "123456"));
    }
}
