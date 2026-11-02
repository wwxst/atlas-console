package com.web.project.channel.vo;

/** SMTP 邮箱登录配置响应，不回传密码或授权码。 */
public record EmailLoginSettingsVO(
        String host,
        int port,
        String username,
        String fromAddress,
        String fromName,
        String security,
        boolean passwordConfigured,
        boolean configured
) {
}
