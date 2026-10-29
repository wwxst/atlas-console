package com.web.project.auth.service;

import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.presence.AuthPresenceStore;
import com.web.project.auth.session.AuthSessionData;
import com.web.project.auth.session.AuthSessionStore;
import com.web.project.common.exception.BusinessException;
import com.web.project.config.RefreshTokenDecoder;
import com.web.project.config.properties.JwtProperties;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class AuthSessionServiceUnitTest {
    private final AuthSessionStore sessions = mock(AuthSessionStore.class);
    private final JwtTokenService tokens = mock(JwtTokenService.class);
    private final AuthPresenceStore presence = mock(AuthPresenceStore.class);
    private final RefreshTokenDecoder decoder = mock(RefreshTokenDecoder.class);
    private final SysUserMapper sysUsers = mock(SysUserMapper.class);
    private final UserAccountMapper users = mock(UserAccountMapper.class);
    private final AuthSessionService service = new AuthSessionService(
            sessions, presence, tokens, decoder, sysUsers, users, mock(JwtProperties.class));

    @Test
    void authenticationVersionChangeRejectsRefreshOnEveryAdminSession() {
        when(decoder.decode("refresh-a")).thenReturn(refresh("device-a", "authVersion", 0L));
        when(decoder.decode("refresh-b")).thenReturn(refresh("device-b", "authVersion", 0L));
        when(sessions.find(AccountType.SYS_USER, "device-a"))
                .thenReturn(new AuthSessionData(7L, null, 0L));
        when(sessions.find(AccountType.SYS_USER, "device-b"))
                .thenReturn(new AuthSessionData(7L, null, 0L));
        SysUser admin = new SysUser();
        admin.setId(7L);
        admin.setStatus(1);
        admin.setAuthVersion(1L);
        when(sysUsers.selectById(7L)).thenReturn(admin);

        assertThrows(BusinessException.class, () -> service.refresh("refresh-a", AccountType.SYS_USER));
        assertThrows(BusinessException.class, () -> service.refresh("refresh-b", AccountType.SYS_USER));
        verifyNoInteractions(tokens);
    }

    @Test
    void reenabledAdminCannotReusePreDisableRefreshToken() {
        when(decoder.decode("refresh-before-disable"))
                .thenReturn(refresh("device", "authVersion", 0L));
        when(sessions.find(AccountType.SYS_USER, "device"))
                .thenReturn(new AuthSessionData(7L, null, 0L));
        SysUser admin = new SysUser();
        admin.setId(7L);
        admin.setStatus(1);
        admin.setAuthVersion(1L);
        when(sysUsers.selectById(7L)).thenReturn(admin);

        assertThrows(BusinessException.class,
                () -> service.refresh("refresh-before-disable", AccountType.SYS_USER));
        verifyNoInteractions(tokens);
    }

    @Test
    void ordinaryUserRefreshContinuesUsingPasswordVersion() {
        when(decoder.decode("user-refresh")).thenReturn(refresh("user-device", "passwordVersion", 3L,
                AccountType.USER));
        when(sessions.find(AccountType.USER, "user-device"))
                .thenReturn(new AuthSessionData(12L, 3L, null));
        UserAccount user = new UserAccount();
        user.setId(12L);
        user.setStatus(1);
        user.setPasswordVersion(3L);
        when(users.selectById(12L)).thenReturn(user);
        when(tokens.createAccessToken(AccountType.USER, 12L, "user-device", 3L)).thenReturn("new-access");

        assertNotNull(service.refresh("user-refresh", AccountType.USER).accessToken());
        verify(tokens).createAccessToken(AccountType.USER, 12L, "user-device", 3L);
    }

    private Jwt refresh(String sessionId, String versionClaim, long version) {
        return refresh(sessionId, versionClaim, version, AccountType.SYS_USER);
    }

    private Jwt refresh(String sessionId, String versionClaim, long version, AccountType accountType) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("signed-refresh")
                .header("alg", "HS256")
                .subject("" + (accountType == AccountType.SYS_USER ? 7 : 12))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("accountType", accountType.name())
                .claim("sessionId", sessionId)
                .claim(versionClaim, version)
                .build();
    }
}
