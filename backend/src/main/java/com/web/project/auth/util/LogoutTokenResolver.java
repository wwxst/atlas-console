package com.web.project.auth.util;

import com.web.project.auth.model.AccountType;
import com.web.project.config.LogoutTokenDecoder;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/** Parses and verifies the signed Access Token used by the idempotent logout endpoints. */
@Component
@RequiredArgsConstructor
public class LogoutTokenResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final LogoutTokenDecoder decoder;

    public String resolveSessionId(String authorization, AccountType expectedAccountType) {
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID, exception);
        }

        if (!expectedAccountType.name().equals(jwt.getClaimAsString("accountType"))) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }
        return JwtClaims.sessionId(jwt);
    }
}
