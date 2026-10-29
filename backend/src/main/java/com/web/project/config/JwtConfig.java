package com.web.project.config;

import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.user.mapper.UserAccountMapper;
import com.web.project.auth.session.AuthSessionStore;
import com.web.project.auth.model.TokenType;
import com.web.project.config.properties.JwtProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * JWT 签名与解码配置；Access 和 Refresh 共用密钥，各自限制允许的 Token 类型。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    /**
     * 根据 application.yml 中的 Base64 密钥，
     * 创建用于 HS256 签名的 SecretKey。
     */
    @Bean
    public SecretKey jwtSecretKey(JwtProperties jwtProperties) {
        if (jwtProperties.secret() == null || jwtProperties.secret().isBlank()) {
            throw new IllegalStateException("必须配置 app.jwt.secret（JWT_SECRET）");
        }
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(jwtProperties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "app.jwt.secret 必须是正确的 Base64 字符串",
                    exception
            );
        }

        // HS256 要求至少 256 位密钥；无效配置在启动时拒绝。
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret 解码后不能少于32字节"
            );
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    /**
     * 使用 HS256 签名的 JWT 编码器。
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder
                .withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * 资源服务器使用的 Access Token 解码器。
     *
     * 校验签名、issuer、标准时间字段，并拒绝 tokenType != access 的 Token。
     */
    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties jwtProperties,
            ObjectProvider<SysUserMapper> sysUserMappers,
            ObjectProvider<UserAccountMapper> userAccounts,
            ObjectProvider<AuthSessionStore> sessionStores
    ) {
        return buildDecoder(jwtSecretKey, jwtProperties, TokenType.ACCESS,
                sysUserMappers.getIfAvailable(), userAccounts.getIfAvailable(), sessionStores.getIfAvailable());
    }

    /**
     * 独立的 Refresh Token 校验入口。
     *
     * 只接受 tokenType=refresh 的 Token，供 Refresh 接口使用。
     */
    @Bean
    public RefreshTokenDecoder refreshTokenDecoder(
            SecretKey jwtSecretKey,
            JwtProperties jwtProperties
    ) {
        return new RefreshTokenDecoder(
                buildDecoder(jwtSecretKey, jwtProperties, TokenType.REFRESH, null, null, null)
        );
    }

    /**
     * 按指定 Token 类型构建解码器。
     *
     * @param jwtSecretKey  签名密钥
     * @param jwtProperties JWT 配置
     * @param tokenType     允许的 Token 类型
     * @return 配置好校验器的解码器
     */
    private JwtDecoder buildDecoder(
            SecretKey jwtSecretKey,
            JwtProperties jwtProperties,
            TokenType tokenType,
            SysUserMapper sysUserMapper,
            UserAccountMapper userAccountMapper,
            AuthSessionStore sessionStore
    ) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        // 两种 Token 都验证签发者与时间字段，再按入口隔离用途。
        OAuth2TokenValidator<Jwt> validator = tokenType == TokenType.ACCESS
                ? new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()),
                        new TokenTypeValidator(tokenType),
                        new AccessSessionValidator(sessionStore),
                        new SysUserTokenValidator(sysUserMapper),
                        new UserTokenValidator(userAccountMapper, sessionStore))
                : new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()),
                        new TokenTypeValidator(tokenType));

        jwtDecoder.setJwtValidator(validator);

        return jwtDecoder;
    }
}
