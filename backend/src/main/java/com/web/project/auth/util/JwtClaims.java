package com.web.project.auth.util;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Access Token 公共 claim 读取工具。
 *
 * 两个 /me 接口和两个 Logout 接口都从 JWT 读取账号 ID 和会话 ID，统一在此处理。
 */
public final class JwtClaims {

    private JwtClaims() {
    }

    /**
     * 从 JWT 的 sub 中读取账号 ID。
     *
     * @param jwt 已通过校验的 Access Token
     * @return 账号主键
     */
    public static Long accountId(Jwt jwt) {
        String subject = jwt == null ? null : jwt.getSubject();

        if (subject == null) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID, exception);
        }
    }

    /**
     * 从 JWT 的 sessionId claim 中读取当前会话 ID。
     *
     * @param jwt 已通过校验的 Access Token
     * @return 会话 ID
     */
    public static String sessionId(Jwt jwt) {
        String sessionId = jwt == null ? null : jwt.getClaimAsString("sessionId");

        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        return sessionId;
    }
}
