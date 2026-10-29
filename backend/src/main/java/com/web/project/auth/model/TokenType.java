package com.web.project.auth.model;

/**
 * Token 类型。
 *
 * 资源接口只接受 Access Token，Refresh 接口只接受 Refresh Token。
 */
public enum TokenType {

    /**
     * 访问令牌，用于调用业务接口。
     */
    ACCESS("access"),

    /**
     * 刷新令牌，仅用于换取新的 Access Token。
     */
    REFRESH("refresh");

    /**
     * JWT 中 tokenType claim 的值。
     */
    private final String value;

    TokenType(String value) {
        this.value = value;
    }

    /**
     * @return tokenType claim 值：access 或 refresh
     */
    public String value() {
        return value;
    }
}
