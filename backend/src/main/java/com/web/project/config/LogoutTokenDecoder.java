package com.web.project.config;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Verifies the signature, issuer, lifetime and token type of an Access Token
 * without requiring its Redis session to remain active.
 *
 * This narrowly scoped decoder lets logout remain idempotent after the first
 * request has already removed the session. It must only be used by logout.
 */
public class LogoutTokenDecoder {

    private final JwtDecoder delegate;

    LogoutTokenDecoder(JwtDecoder delegate) {
        this.delegate = delegate;
    }

    public Jwt decode(String accessToken) {
        return delegate.decode(accessToken);
    }
}
