package com.web.project.auth.model;

/**
 * 账号类型。
 *
 * 决定 Token 中的 accountType、scope 值，以及 Redis 会话 key 中的账号类型段。
 */
public enum AccountType {

    /**
     * 系统用户，使用用户名和密码登录，拥有 admin 权限。
     */
    SYS_USER("admin"),

    /**
     * 普通用户，使用手机号或邮箱登录，拥有 user 权限。
     */
    USER("user");

    /**
     * 该账号类型对应的 JWT scope 值。
     */
    private final String scope;

    AccountType(String scope) {
        this.scope = scope;
    }

    /**
     * @return JWT scope 值：admin 或 user
     */
    public String scope() {
        return scope;
    }
}
