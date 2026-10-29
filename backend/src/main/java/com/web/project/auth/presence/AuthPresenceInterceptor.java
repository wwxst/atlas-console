package com.web.project.auth.presence;

import com.web.project.auth.model.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Record authenticated API activity after Spring Security's authentication and
 * authorization filters have accepted the request, rather than relying on a
 * refresh token's thirty-day lifetime as an online signal.
 */
@Component
@RequiredArgsConstructor
public class AuthPresenceInterceptor implements HandlerInterceptor {
    private final AuthPresenceStore presenceStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth && authentication.isAuthenticated()) {
            try {
                AccountType type = AccountType.valueOf(jwtAuth.getToken().getClaimAsString("accountType"));
                Long accountId = Long.valueOf(jwtAuth.getToken().getSubject());
                presenceStore.touch(type, accountId);
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // Invalid JWT claims never contribute to online presence.
            } catch (DataAccessException ignored) {
                // Presence telemetry is best-effort; authorization has already
                // validated the Redis login session separately and fails closed.
            }
        }
        return true;
    }
}
