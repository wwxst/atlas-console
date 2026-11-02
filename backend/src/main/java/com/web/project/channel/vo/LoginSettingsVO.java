package com.web.project.channel.vo;

/** 登录基础配置响应。 */
public record LoginSettingsVO(
        boolean phoneLoginEnabled,
        boolean emailLoginEnabled,
        String smsProvider
) {
}
