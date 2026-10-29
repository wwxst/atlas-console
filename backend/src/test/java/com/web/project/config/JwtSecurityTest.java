package com.web.project.config;

import com.web.project.admin.service.SysUserService;
import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.session.AuthSessionData;
import com.web.project.auth.session.AuthSessionStore;
import com.web.project.auth.service.JwtTokenService;
import com.web.project.common.result.PageResult;
import com.web.project.config.properties.JwtProperties;
import com.web.project.user.service.UserProfileService;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import com.web.project.user.service.UserAccountService;
import com.web.project.user.dto.UpdateUserAccountDTO;
import com.web.project.user.vo.UserAccountListVO;
import com.web.project.user.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 测试密钥只存在于测试配置，生产配置仍必须显式提供 JWT_SECRET。
@SpringBootTest(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class JwtSecurityTest {
    private static final String ADMIN_PATH = "/api/sys-user/sys-users";
    private static final String USER_PATH = "/api/user/me";

    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired RefreshTokenDecoder refreshTokenDecoder;
    @Autowired JwtProperties properties;
    @MockitoBean SysUserService admins;
    @MockitoBean SysUserMapper sysUserMapper;
    @MockitoBean UserAccountMapper userAccountMapper;
    @MockitoBean AuthSessionStore authSessionStore;
    @MockitoBean UserProfileService profiles;
    @MockitoBean UserAccountService users;

    @BeforeEach
    void stubActiveAdmin() {
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setStatus(1);
        admin.setAuthVersion(0L);
        when(sysUserMapper.selectById(1L)).thenReturn(admin);
        when(authSessionStore.find(AccountType.SYS_USER, "session-admin"))
                .thenReturn(new AuthSessionData(1L, null, 0L));
        when(authSessionStore.find(AccountType.USER, "session-user"))
                .thenReturn(new AuthSessionData(2L, 0L, null));
        UserAccount activeUser = new UserAccount();
        activeUser.setId(2L);
        activeUser.setStatus(1);
        activeUser.setPasswordVersion(0L);
        when(userAccountMapper.selectById(2L)).thenReturn(activeUser);
    }

    @Test
    void storageManagementRequiresAdminAndOnlyFileReadsArePublic() throws Exception {
        mvc.perform(get("/api/sys-user/storage/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/sys-user/storage/settings")
                        .header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/files/not-an-id")).andExpect(status().isBadRequest());
        mvc.perform(head("/files/not-an-id")).andExpect(status().isBadRequest());
        mvc.perform(post("/files/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanEditOrdinaryUserButUserAnonymousAndRefreshCannot() throws Exception {
        long userId = 9007199254740993L;
        String path = "/api/sys-user/users/" + userId;
        String payload = "{\"nickname\":\"昵称\",\"phone\":null,\"email\":\"new@example.com\"}";
        when(users.updateUser(eq(userId), any())).thenReturn(new UserAccountListVO(
                userId, "123456", null, "new@example.com", "昵称", null, 0, LocalDateTime.now(), LocalDateTime.now()));
        mvc.perform(put(path).contentType("application/json").content(payload)
                        .header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(Long.toString(userId)))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordVersion").doesNotExist());
        verify(users).updateUser(userId, new UpdateUserAccountDTO("昵称", null, "new@example.com"));
        clearInvocations(users);
        mvc.perform(put(path).contentType("application/json").content(payload))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(path).contentType("application/json").content(payload)
                        .header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        mvc.perform(put(path).contentType("application/json").content(payload)
                        .header("Authorization", "Bearer " + tokens.createRefreshToken(
                                AccountType.SYS_USER, 1L, "session-admin", null)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @Test
    void ordinaryUserEditValidatesNicknameAndRequiresOneContact() throws Exception {
        for (String nickname : List.of(" ", "长".repeat(51))) {
            mvc.perform(put("/api/sys-user/users/5").contentType("application/json")
                            .content("{\"nickname\":\"" + nickname + "\",\"email\":\"new@example.com\"}")
                            .header("Authorization", "Bearer " + adminAccessToken()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        }
        mvc.perform(put("/api/sys-user/users/5").contentType("application/json")
                        .content("{\"nickname\":\"昵称\",\"phone\":\" \",\"email\":null}")
                        .header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(users);
    }

    private String adminAccessToken() {
        return tokens.createAccessToken(AccountType.SYS_USER, 1L, "session-admin", 0L);
    }

    @Test
    void logoutRevokesAlreadyIssuedAccessTokensForBothAccountTypes() throws Exception {
        String adminAccess = adminAccessToken();
        String userAccess = userAccessToken();
        when(admins.getSysUserPage(any())).thenReturn(new PageResult<>(0L, 1, 10, List.of()));
        when(profiles.getProfile(2L)).thenReturn(new UserInfoVO(
                2L, "123456", "13800138000", null, "用户", null, 1, LocalDateTime.now()));

        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + adminAccess))
                .andExpect(status().isOk());
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + userAccess))
                .andExpect(status().isOk());

        // Logout deletes the corresponding Redis session. The same signed JWT
        // must now fail without waiting for its 30-minute expiration.
        when(authSessionStore.find(AccountType.SYS_USER, "session-admin")).thenReturn(null);
        when(authSessionStore.find(AccountType.USER, "session-user")).thenReturn(null);

        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + adminAccess))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + userAccess))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAdminAccessTokenIsRejectedImmediately() throws Exception {
        sysUserMapper.selectById(1L).setStatus(0);

        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }

    @Test
    void resetPasswordVersionRejectsOldAccessTokenImmediately() throws Exception {
        sysUserMapper.selectById(1L).setAuthVersion(1L);

        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }

    @Test
    void userPasswordChangeImmediatelyRejectsPreviouslyIssuedAccessToken() throws Exception {
        String access = userAccessToken();
        when(profiles.getProfile(2L)).thenReturn(new UserInfoVO(
                2L, "123456", "13800138000", null, "用户", null, 1, LocalDateTime.now()));
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());

        // A committed password reset increments password_version in the database.
        userAccountMapper.selectById(2L).setPasswordVersion(1L);
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        verify(profiles, times(1)).getProfile(2L);
    }

    @Test
    void disabledOrDeletedUserImmediatelyRejectsPreviouslyIssuedAccessToken() throws Exception {
        String access = userAccessToken();
        userAccountMapper.selectById(2L).setStatus(0);
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));

        when(userAccountMapper.selectById(2L)).thenReturn(null);
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(profiles);
    }

    @Test
    void userAccessTokenRejectsMissingOrInconsistentPasswordVersion() throws Exception {
        String access = userAccessToken();
        when(authSessionStore.find(AccountType.USER, "session-user"))
                .thenReturn(new AuthSessionData(2L, 7L, null));
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));

        when(authSessionStore.find(AccountType.USER, "session-user"))
                .thenReturn(new AuthSessionData(2L, 0L, null));
        String noVersion = tokens.createAccessToken(AccountType.USER, 2L, "session-user", null);
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + noVersion))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
    }

    @Test
    void userDatabaseFailureNeverAuthorizesOldAccessToken() throws Exception {
        when(userAccountMapper.selectById(2L))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db unavailable"));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,
                () -> mvc.perform(get(USER_PATH)
                        .header("Authorization", "Bearer " + userAccessToken())));
        verifyNoInteractions(profiles);
    }

    @Test
    void ordinaryUserStatusRequiresAdminAccessAndValidStatus() throws Exception {
        String path = "/api/sys-user/users/5/status";
        for (int userStatus : List.of(0, 1)) {
            mvc.perform(patch(path).contentType("application/json")
                            .content("{\"status\":" + userStatus + "}")
                            .header("Authorization", "Bearer " + adminAccessToken()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
            verify(users).updateStatus(5L, userStatus);
        }
        clearInvocations(users);
        for (String payload : List.of("{}", "{\"status\":-1}", "{\"status\":2}")) {
            mvc.perform(patch(path).contentType("application/json").content(payload)
                            .header("Authorization", "Bearer " + adminAccessToken()))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        }
        mvc.perform(patch(path).contentType("application/json").content("{\"status\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(path).contentType("application/json").content("{\"status\":0}")
                        .header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path).contentType("application/json").content("{\"status\":0}")
                        .header("Authorization", "Bearer " + tokens.createRefreshToken(
                                AccountType.SYS_USER, 1L, "session-admin", null)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    private String userAccessToken() {
        return tokens.createAccessToken(AccountType.USER, 2L, "session-user", 0L);
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get(ADMIN_PATH)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }

    @Test
    void realAdminTokenCanAccessAdminApiButNotUserApi() throws Exception {
        when(admins.getSysUserPage(any())).thenReturn(new PageResult<>(0L, 1, 10, List.of()));
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        verifyNoInteractions(profiles);
    }

    @Test
    void realUserTokenCanAccessUserApiButNotAdminApi() throws Exception {
        when(profiles.getProfile(2L)).thenReturn(new UserInfoVO(
                2L, "123456", "13800138000", null, "用户", null, 1, LocalDateTime.now()
        ));
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(profiles).getProfile(2L);
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        verifyNoInteractions(admins);
    }

    @Test
    void refreshTokenCannotAccessResourceEndpoints() throws Exception {
        // Refresh Token 的 tokenType 为 refresh，资源服务器只接受 access
        String refreshToken = tokens.createRefreshToken(
                AccountType.SYS_USER, 1L, "session-admin", null
        );
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }

    @Test
    void accessTokenCannotBeUsedAtRefreshEndpoint() throws Exception {
        // Access Token 的 tokenType 为 access，Refresh 入口只接受 refresh
        for (String path : List.of("/api/sys-user/auth/refresh", "/api/user/auth/refresh")) {
            mvc.perform(post(path).contentType("application/json")
                            .content("{\"refreshToken\":\"" + adminAccessToken() + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40104));
        }
    }

    @Test
    void authEndpointsArePublicAndStillValidateInput() throws Exception {
        for (String path : List.of(
                "/api/sys-user/auth/login",
                "/api/sys-user/auth/refresh",
                "/api/user/auth/login",
                "/api/user/auth/refresh",
                "/api/user/auth/register",
                "/api/user/auth/reset-password"
        )) {
            mvc.perform(post(path).contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        }
        verifyNoInteractions(admins, profiles);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        assertUnauthorized(encode(encoder, properties.issuer(), Instant.now().minusSeconds(300)));
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        assertUnauthorized(encode(encoder, "another-service", Instant.now().plusSeconds(300)));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String otherSecret = Base64.getEncoder().encodeToString(new byte[32]);
        var otherProperties = new JwtProperties(
                properties.issuer(), otherSecret, Duration.ofHours(1), Duration.ofDays(30)
        );
        var config = new JwtConfig();
        var otherEncoder = config.jwtEncoder(config.jwtSecretKey(otherProperties));
        assertUnauthorized(encode(otherEncoder, properties.issuer(), Instant.now().plusSeconds(300)));
    }

    @Test
    void tokenExpirationsMatchConfiguredDurations() {
        // Access Token 默认 30 分钟，Refresh Token 默认 30 天
        var access = jwtDecoder.decode(tokens.createAccessToken(
                AccountType.USER, 2L, "session-user", 0L
        ));
        assertEquals(1800L, Duration.between(
                access.getIssuedAt(), access.getExpiresAt()
        ).getSeconds());

        var refresh = refreshTokenDecoder.decode(tokens.createRefreshToken(
                AccountType.USER, 2L, "session-user", 0L
        ));
        assertEquals(2_592_000L, Duration.between(
                refresh.getIssuedAt(), refresh.getExpiresAt()
        ).getSeconds());
    }

    @Test
    void removedBusinessEndpointsReturnNotFound() throws Exception {
        for (String path : List.of("/api/sys-user/products", "/api/sys-user/products/1/plans",
                "/api/sys-user/redeem-code-batches", "/api/sys-user/redeem-records", "/api/sys-user/user-subscriptions")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + adminAccessToken()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/sys-user/products").contentType("application/json").content("{}")
                        .header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/user/subscription").header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/user/redemptions").contentType("application/json").content("{}")
                        .header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(admins, profiles);
    }

    private String encode(JwtEncoder signer, String issuer, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("1")
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiresAt)
                .claim("scope", "admin").claim("tokenType", "access").build();
        return signer.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }
}
