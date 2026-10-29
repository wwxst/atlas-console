package com.web.project.config;

import com.web.project.auth.model.AccountType;
import com.web.project.auth.session.AuthSessionData;
import com.web.project.auth.session.AuthSessionStore;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * For every ordinary-user Access Token, check current database state and credential
 * version. A reset/change-password or account disable invalidates issued Access
 * Tokens immediately, not just Refresh Tokens. No cache or fail-open fallback.
 */
final class UserTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final UserAccountMapper accounts;
    private final AuthSessionStore sessions;

    UserTokenValidator(UserAccountMapper accounts, AuthSessionStore sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!AccountType.USER.name().equals(token.getClaimAsString("accountType"))) {
            return OAuth2TokenValidatorResult.success();
        }
        if (accounts == null || sessions == null) {
            return invalidToken();
        }

        try {
            Long accountId = Long.valueOf(token.getSubject());
            String sessionId = token.getClaimAsString("sessionId");
            Object rawVersion = token.getClaim("passwordVersion");
            if (sessionId == null || sessionId.isBlank() || rawVersion == null) {
                return invalidToken();
            }
            Long version = Long.valueOf(rawVersion.toString());
            AuthSessionData session = sessions.find(AccountType.USER, sessionId);
            if (session == null || !accountId.equals(session.accountId())
                    || session.passwordVersion() == null
                    || !version.equals(session.passwordVersion())) {
                return invalidToken();
            }

            UserAccount account = accounts.selectById(accountId);
            if (account != null && Integer.valueOf(1).equals(account.getStatus())
                    && version.equals(account.getPasswordVersion())) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException | NullPointerException exception) {
            // Reject malformed credentials. Database/Redis outages propagate and fail closed.
        }

        return invalidToken();
    }

    private OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "用户登录状态已失效", null));
    }
}
