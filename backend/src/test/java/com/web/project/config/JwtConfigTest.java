package com.web.project.config;

import com.web.project.config.properties.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JwtConfigTest {
    @Test
    void missingMalformedAndShortSecretsFailBeforeTokenIssuance() {
        for (String secret : new String[]{null, "", " ", "not-base64!", "c2hvcnQ="}) {
            var properties = new JwtProperties(
                    "web-project",
                    secret,
                    Duration.ofMinutes(30),
                    Duration.ofDays(30)
            );
            assertThrows(IllegalStateException.class, () -> new JwtConfig().jwtSecretKey(properties));
        }
    }

    @Test
    void invalidIssuerAndDurationsFailWhenConfigurationIsBound() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(JwtConfig.class)
                .withPropertyValues(
                        "app.jwt.issuer=web-project",
                        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                        "app.jwt.access-token-expiration=30m",
                        "app.jwt.refresh-token-expiration=30d"
                );
        for (String invalid : new String[]{
                "app.jwt.issuer=",
                "app.jwt.access-token-expiration=0s",
                "app.jwt.access-token-expiration=-1s",
                "app.jwt.refresh-token-expiration=0s",
                "app.jwt.refresh-token-expiration=500ms"
        }) {
            runner.withPropertyValues(invalid)
                    .run(context -> assertNotNull(context.getStartupFailure()));
        }
        runner.run(context -> assertNull(context.getStartupFailure()));
    }
}
