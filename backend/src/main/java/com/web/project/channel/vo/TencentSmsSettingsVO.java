package com.web.project.channel.vo;

/** 腾讯云短信配置响应，不回传 SecretKey。 */
public record TencentSmsSettingsVO(
        String secretId,
        String sdkAppId,
        String signName,
        String templateId,
        String region,
        boolean secretKeyConfigured,
        boolean configured
) {
}
