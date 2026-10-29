package com.web.project.config;

import com.web.project.auth.model.AccountType;
import com.web.project.auth.session.AuthSessionData;
import com.web.project.auth.session.AuthSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessSessionValidatorTest {
    private final AuthSessionStore sessions = mock(AuthSessionStore.class);
    private final AccessSessionValidator validator = new AccessSessionValidator(sessions);

    private Jwt token(String accountType, String sessionId, String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("signed-access")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("accountType", accountType)
                .claim("sessionId", sessionId)
                .build();
    }

    @Test
    void validSessionMustMatchTheSignedAccountId() {
        Jwt userToken = token("USER", "device-1", "42");
        when(sessions.find(AccountType.USER, "device-1"))
                .thenReturn(new AuthSessionData(42L, 0L, null));
        assertFalse(validator.validate(userToken).hasErrors());

        when(sessions.find(AccountType.USER, "device-1"))
                .thenReturn(new AuthSessionData(99L, 0L, null));
        assertTrue(validator.validate(userToken).hasErrors());
    }

    @Test
    void removedSessionOrMalformedClaimsAreRejected() {
        assertTrue(validator.validate(token("SYS_USER", "revoked", "7")).hasErrors());
        assertTrue(validator.validate(token("UNKNOWN", "device", "7")).hasErrors());
        assertTrue(validator.validate(token("USER", "", "42")).hasErrors());
        assertTrue(validator.validate(token("USER", "device", "malformed")).hasErrors());
        assertTrue(new AccessSessionValidator(null)
                .validate(token("USER", "device", "42")).hasErrors());
    }

    @Test
    void redisFailureMustNotMakeAnAccessTokenValid() {
        when(sessions.find(AccountType.USER, "device-1"))
                .thenThrow(new RedisConnectionFailureException("offline"));
        assertThrows(RedisConnectionFailureException.class,
                () -> validator.validate(token("USER", "device-1", "42")));
    }
}
