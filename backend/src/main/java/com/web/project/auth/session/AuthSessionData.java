package com.web.project.auth.session;

/**
 * Redis 会话数据。
 *
 * 保留普通用户现有 Redis 字段名，并为系统用户单独保存认证版本。
 *
 * @param accountId       账号主键
 * @param passwordVersion 普通用户密码版本
 * @param authVersion 系统用户认证版本
 */
public record AuthSessionData(
        Long accountId,
        Long passwordVersion,
        Long authVersion
) {
}
