package com.web.project.auth.presence;

import com.web.project.auth.model.AccountType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthPresenceStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> operations = mock(ValueOperations.class);
    private final AuthPresenceStore store = new AuthPresenceStore(redis);

    @Test
    void authenticatedActivityExpiresAndSeparatesAccountTypes() {
        when(redis.opsForValue()).thenReturn(operations);

        store.touch(AccountType.USER, 12L);
        store.touch(AccountType.SYS_USER, 12L);

        verify(operations).set("auth:presence:USER:12", "1", AuthPresenceStore.ONLINE_WINDOW);
        verify(operations).set("auth:presence:SYS_USER:12", "1", AuthPresenceStore.ONLINE_WINDOW);
        assertEquals(java.time.Duration.ofMinutes(3), AuthPresenceStore.ONLINE_WINDOW);
    }

    @Test
    void pageChecksUseOneMultiGetAndIgnoreMissingActivity() {
        when(redis.opsForValue()).thenReturn(operations);
        List<String> keys = List.of("auth:presence:USER:1", "auth:presence:USER:2", "auth:presence:USER:3");
        when(operations.multiGet(keys)).thenReturn(Arrays.asList("1", null, "1"));

        assertEquals(Set.of(1L, 3L), store.onlineIds(AccountType.USER, List.of(1L, 2L, 3L)));
        verify(operations, times(1)).multiGet(keys);
        assertEquals(Set.of(), store.onlineIds(AccountType.USER, List.of()));
        verifyNoMoreInteractions(operations);
    }
}
