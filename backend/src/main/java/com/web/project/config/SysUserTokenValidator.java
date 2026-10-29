package com.web.project.config;

import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.auth.model.AccountType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Checks current administrator state on every protected request without a cache. */
final class SysUserTokenValidator implements OAuth2TokenValidator<Jwt> {
    private final SysUserMapper sysUserMapper;

    SysUserTokenValidator(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!AccountType.SYS_USER.name().equals(token.getClaimAsString("accountType"))) {
            return OAuth2TokenValidatorResult.success();
        }

        if (sysUserMapper == null) {
            return invalidToken();
        }

        try {
            Long accountId = Long.valueOf(token.getSubject());
            Object rawVersion = token.getClaim("authVersion");
            Long tokenVersion = rawVersion == null ? null : Long.valueOf(rawVersion.toString());
            SysUser current = sysUserMapper.selectById(accountId);
            if (current != null && Integer.valueOf(1).equals(current.getStatus())
                    && tokenVersion != null && tokenVersion.equals(current.getAuthVersion())) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (IllegalArgumentException exception) {
            // Malformed administrator claims are rejected as invalid credentials.
        }

        return invalidToken();
    }

    private OAuth2TokenValidatorResult invalidToken() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token", "系统用户登录状态已失效", null));
    }
}
