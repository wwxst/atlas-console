package com.web.project.config;

import com.web.project.auth.model.TokenType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Token 类型校验器。
 *
 * 资源服务器只接受 tokenType=access，Refresh 入口只接受 tokenType=refresh。
 */
class TokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private final TokenType expected;

    TokenTypeValidator(TokenType expected) {
        this.expected = expected;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (expected.value().equals(token.getClaimAsString("tokenType"))) {
            return OAuth2TokenValidatorResult.success();
        }

        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "令牌类型不正确",
                null
        ));
    }
}
