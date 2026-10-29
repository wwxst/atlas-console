package com.web.project.auth.service;

import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.common.exception.BusinessException;
import com.web.project.config.RefreshTokenDecoder;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 使用本地真实 Redis 和 mock Mapper 验证公共会话的创建、Refresh 和 Logout。
 */
@SpringBootTest(properties = "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
class AuthSessionServiceTest {

    @Autowired AuthSessionService authSessionService;
    @Autowired RefreshTokenDecoder refreshTokenDecoder;
    @Autowired JwtDecoder accessTokenDecoder;
    @Autowired StringRedisTemplate redisTemplate;
    @MockitoBean SysUserMapper sysUserMapper;
    @MockitoBean UserAccountMapper userAccountMapper;

    private final List<String> sessionIds = new ArrayList<>();

    @BeforeEach
    void requireRedis() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.ping();
        } catch (RedisConnectionFailureException exception) {
            Assumptions.assumeTrue(false, "本地 Redis 不可用，跳过真实 Redis 会话测试");
        }
    }

    @AfterEach
    void cleanUp() {
        for (String sessionId : sessionIds) {
            redisTemplate.delete("auth:session:SYS_USER:" + sessionId);
            redisTemplate.delete("auth:session:USER:" + sessionId);
        }
        sessionIds.clear();
    }

    private String sessionIdOf(String refreshToken) {
        return refreshTokenDecoder.decode(refreshToken).getClaimAsString("sessionId");
    }

    private void track(TokenPairVO pair) {
        sessionIds.add(sessionIdOf(pair.refreshToken()));
    }

    private SysUser activeSysUser(Long id) {
        SysUser sysUser = new SysUser();
        sysUser.setId(id);
        sysUser.setUsername("admin");
        sysUser.setStatus(1);
        sysUser.setAuthVersion(0L);
        return sysUser;
    }

    private UserAccount activeUser(Long id, Long passwordVersion) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setStatus(1);
        user.setPasswordVersion(passwordVersion);
        return user;
    }

    @Test
    void logoutImmediatelyRevokesSignedAccessTokens() {
        when(sysUserMapper.selectById(1L)).thenReturn(activeSysUser(1L));
        TokenPairVO admin = authSessionService.createSession(AccountType.SYS_USER, 1L, 0L);
        track(admin);
        assertNotNull(accessTokenDecoder.decode(admin.accessToken()));
        authSessionService.logout(AccountType.SYS_USER, sessionIdOf(admin.refreshToken()));
        assertThrows(JwtException.class, () -> accessTokenDecoder.decode(admin.accessToken()));

        when(userAccountMapper.selectById(2L)).thenReturn(activeUser(2L, 0L));
        TokenPairVO user = authSessionService.createSession(AccountType.USER, 2L, 0L);
        track(user);
        assertNotNull(accessTokenDecoder.decode(user.accessToken()));
        authSessionService.logout(AccountType.USER, sessionIdOf(user.refreshToken()));
        assertThrows(JwtException.class, () -> accessTokenDecoder.decode(user.accessToken()));
    }

    @Test
    void sysUserSessionRefreshReturnsNewAccessToken() {
        when(sysUserMapper.selectById(1L)).thenReturn(activeSysUser(1L));

        TokenPairVO pair = authSessionService.createSession(AccountType.SYS_USER, 1L, 0L);
        track(pair);

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());

        AccessTokenVO refreshed = authSessionService.refresh(pair.refreshToken(), AccountType.SYS_USER);
        assertNotNull(refreshed.accessToken());
    }

    @Test
    void realRedisUserSessionsRejectAccessImmediatelyAfterPasswordResetDisableAndLogout() {
        UserAccount user = activeUser(2L, 5L);
        when(userAccountMapper.selectById(2L)).thenReturn(user);
        TokenPairVO pair = authSessionService.createSession(AccountType.USER, 2L, 5L);
        track(pair);
        assertNotNull(accessTokenDecoder.decode(pair.accessToken()));

        user.setPasswordVersion(6L);
        assertThrows(JwtException.class, () -> accessTokenDecoder.decode(pair.accessToken()));

        user.setPasswordVersion(5L);
        user.setStatus(0);
        assertThrows(JwtException.class, () -> accessTokenDecoder.decode(pair.accessToken()));

        user.setStatus(1);
        authSessionService.logout(AccountType.USER, sessionIdOf(pair.refreshToken()));
        assertThrows(JwtException.class, () -> accessTokenDecoder.decode(pair.accessToken()));
    }

    @Test
    void userSessionRefreshRequiresMatchingPasswordVersion() {
        UserAccount user = activeUser(2L, 5L);
        when(userAccountMapper.selectById(2L)).thenReturn(user);

        TokenPairVO pair = authSessionService.createSession(AccountType.USER, 2L, 5L);
        track(pair);

        // 版本一致时可续期
        assertNotNull(authSessionService.refresh(pair.refreshToken(), AccountType.USER).accessToken());

        // 密码版本变化后旧 Refresh Token 失效
        user.setPasswordVersion(6L);
        assertThrows(
                BusinessException.class,
                () -> authSessionService.refresh(pair.refreshToken(), AccountType.USER)
        );
    }

    @Test
    void deletedSessionRejectsRefresh() {
        when(sysUserMapper.selectById(1L)).thenReturn(activeSysUser(1L));

        TokenPairVO pair = authSessionService.createSession(AccountType.SYS_USER, 1L, 0L);
        authSessionService.logout(AccountType.SYS_USER, sessionIdOf(pair.refreshToken()));

        assertThrows(
                BusinessException.class,
                () -> authSessionService.refresh(pair.refreshToken(), AccountType.SYS_USER)
        );
    }

    @Test
    void disabledSysUserRejectsRefresh() {
        SysUser admin = activeSysUser(1L);
        when(sysUserMapper.selectById(1L)).thenReturn(admin);

        TokenPairVO pair = authSessionService.createSession(AccountType.SYS_USER, 1L, 0L);
        track(pair);

        admin.setStatus(0);

        assertThrows(
                BusinessException.class,
                () -> authSessionService.refresh(pair.refreshToken(), AccountType.SYS_USER)
        );
    }

    @Test
    void sysUserRefreshRejectsAfterDisableAndReenable() {
        SysUser admin = activeSysUser(1L);
        when(sysUserMapper.selectById(1L)).thenReturn(admin);

        TokenPairVO pair = authSessionService.createSession(AccountType.SYS_USER, 1L, 0L);
        track(pair);

        admin.setStatus(0);
        admin.setStatus(1);
        admin.setAuthVersion(1L);
        assertThrows(BusinessException.class,
                () -> authSessionService.refresh(pair.refreshToken(), AccountType.SYS_USER));
    }

    @Test
    void versionChangeRejectsRefreshFromEveryDevice() {
        SysUser admin = activeSysUser(1L);
        when(sysUserMapper.selectById(1L)).thenReturn(admin);
        TokenPairVO firstDevice = authSessionService.createSession(AccountType.SYS_USER, 1L, 0L);
        TokenPairVO secondDevice = authSessionService.createSession(AccountType.SYS_USER, 1L, 0L);
        track(firstDevice);
        track(secondDevice);

        admin.setAuthVersion(1L);

        assertThrows(BusinessException.class,
                () -> authSessionService.refresh(firstDevice.refreshToken(), AccountType.SYS_USER));
        assertThrows(BusinessException.class,
                () -> authSessionService.refresh(secondDevice.refreshToken(), AccountType.SYS_USER));
    }

    @Test
    void accountTypeMismatchRejectsRefresh() {
        when(sysUserMapper.selectById(1L)).thenReturn(activeSysUser(1L));

        TokenPairVO pair = authSessionService.createSession(AccountType.SYS_USER, 1L, 0L);
        track(pair);

        // 系统用户的 Refresh Token 不能用于普通用户接口
        assertThrows(
                BusinessException.class,
                () -> authSessionService.refresh(pair.refreshToken(), AccountType.USER)
        );
    }

    @Test
    void logoutIsIdempotent() {
        assertDoesNotThrow(() -> authSessionService.logout(AccountType.USER, "not-exist"));
    }
}
