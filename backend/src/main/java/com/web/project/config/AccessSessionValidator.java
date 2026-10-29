package com.web.project.config;

import com.web.project.auth.model.AccountType;
import com.web.project.auth.session.AuthSessionData;
import com.web.project.auth.session.AuthSessionStore;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Every Access Token must still belong to a live Redis session.
 * Deleting that session during logout revokes already-issued Access Tokens.
 * Redis failures are not treated as a valid session.
 */
final class AccessSessionValidator implements OAuth2TokenValidator<Jwt> {
    private final AuthSessionStore sessions;

    AccessSessionValidator(AuthSessionStore sessions) {
        this.sessions = sessions;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (sessions == null) {
            return invalidToken();
        }

        try {
            AccountType accountType = AccountType.valueOf(token.getClaimAsString("accountType"));
            String sessionId = token.getClaimAsString("sessionId");
            Long accountId = Long.valueOf(token.getSubject());
            if (sessionId == null || sessionId.isBlank()) {
                return invalidToken();
            }

            AuthSessionData session = sessions.find(accountType, sessionId);
            if (session != null && accountId.equals(session.accountId())) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            // Invalid claims are rejected; infrastructure failures propagate and fail closed.
        }

        return invalidToken();
    }

    private OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "登录会话已失效", null));
    }
}
