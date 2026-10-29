package com.web.project.auth.service;

import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.presence.AuthPresenceStore;
import com.web.project.auth.session.AuthSessionData;
import com.web.project.auth.session.AuthSessionStore;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.config.RefreshTokenDecoder;
import com.web.project.config.properties.JwtProperties;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 共用会话业务类。
 *
 * 两类账号共用会话创建、Refresh 和 Logout。
 * 各类登录 Service 只负责校验自己的凭据，成功后调用本类签发双 Token。
 */
@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final AuthSessionStore sessionStore;
    private final AuthPresenceStore presenceStore;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenDecoder refreshTokenDecoder;
    private final SysUserMapper sysUserMapper;
    private final UserAccountMapper userAccountMapper;
    private final JwtProperties jwtProperties;

    /**
     * 创建新会话并签发双 Token。
     *
     * 每次成功登录或注册都会生成新的 sessionId。
     *
     * @param accountType     账号类型
     * @param accountId       账号主键
     * @param credentialVersion 普通用户密码版本或系统用户认证版本
     * @return Access Token 与 Refresh Token
     */
    public TokenPairVO createSession(
            AccountType accountType,
            Long accountId,
            Long credentialVersion
    ) {
        String sessionId = UUID.randomUUID().toString();

        sessionStore.save(
                accountType,
                sessionId,
                new AuthSessionData(
                        accountId,
                        accountType == AccountType.USER ? credentialVersion : null,
                        accountType == AccountType.SYS_USER ? credentialVersion : null),
                jwtProperties.refreshTokenExpiration()
        );

        String accessToken = jwtTokenService.createAccessToken(
                accountType,
                accountId,
                sessionId,
                credentialVersion
        );

        String refreshToken = jwtTokenService.createRefreshToken(
                accountType,
                accountId,
                sessionId,
                credentialVersion
        );

        presenceStore.touch(accountType, accountId);
        return new TokenPairVO(accessToken, refreshToken);
    }

    /**
     * 使用 Refresh Token 换取新的 Access Token。
     *
     * Refresh Token 在同一个 Session 生命周期内保持不变，因此只签发新的 Access Token。
     *
     * @param refreshToken        前端提交的 Refresh Token
     * @param expectedAccountType 当前接口要求的账号类型
     * @return 新的 Access Token
     */
    public AccessTokenVO refresh(
            String refreshToken,
            AccountType expectedAccountType
    ) {
        Jwt jwt;
        try {
            jwt = refreshTokenDecoder.decode(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, exception);
        }

        /*
         * 账号类型必须与当前接口一致：
         * 系统用户接口只接受 SYS_USER，普通用户接口只接受 USER。
         */
        if (!expectedAccountType.name().equals(
                jwt.getClaimAsString("accountType")
        )) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String sessionId = jwt.getClaimAsString("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long accountId = parseAccountId(jwt);

        /*
         * Redis 故障时这里会抛出连接异常并返回服务器错误，
         * 不会跳过会话校验直接签发新 Token。
         */
        AuthSessionData session = sessionStore.find(
                expectedAccountType,
                sessionId
        );

        if (session == null || !accountId.equals(session.accountId())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long credentialVersion = null;

        if (expectedAccountType == AccountType.SYS_USER) {
            SysUser sysUser = sysUserMapper.selectById(accountId);
            if (sysUser == null
                    || !Integer.valueOf(1).equals(sysUser.getStatus())) {
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
            }
            Long tokenVersion = readVersion(jwt, "authVersion");
            if (session.authVersion() == null
                    || !session.authVersion().equals(sysUser.getAuthVersion())
                    || !session.authVersion().equals(tokenVersion)) {
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
            }
            credentialVersion = sysUser.getAuthVersion();
        } else {
            UserAccount userAccount = userAccountMapper.selectById(accountId);
            if (userAccount == null
                    || !Integer.valueOf(1).equals(userAccount.getStatus())) {
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
            }

            /*
             * 普通用户继续校验数据库密码版本与 Token、Session 一致。
             * 修改或重置密码后版本递增，旧 Refresh Token 无法续期。
             */
            Long currentVersion = userAccount.getPasswordVersion();
            Long tokenVersion = readPasswordVersion(jwt);

            if (session.passwordVersion() == null
                    || !session.passwordVersion().equals(currentVersion)
                    || !session.passwordVersion().equals(tokenVersion)) {
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
            }

            credentialVersion = currentVersion;
        }

        String accessToken = jwtTokenService.createAccessToken(
                expectedAccountType,
                accountId,
                sessionId,
                credentialVersion
        );

        presenceStore.touch(expectedAccountType, accountId);
        return new AccessTokenVO(accessToken);
    }

    /**
     * 删除当前会话。
     *
     * 接口保持幂等：Session 已不存在时同样成功。
     */
    public void logout(AccountType accountType, String sessionId) {
        sessionStore.delete(accountType, sessionId);
    }

    /**
     * 从 JWT 的 sub 中读取账号 ID。
     */
    private Long parseAccountId(Jwt jwt) {
        String subject = jwt.getSubject();

        if (subject == null) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, exception);
        }
    }

    /**
     * 读取普通用户 Token 中的 passwordVersion claim。
     */
    private Long readPasswordVersion(Jwt jwt) {
        return readVersion(jwt, "passwordVersion");
    }

    private Long readVersion(Jwt jwt, String claimName) {
        Object raw = jwt.getClaim(claimName);

        if (raw == null) {
            return null;
        }

        try {
            return Long.valueOf(raw.toString());
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, exception);
        }
    }
}
