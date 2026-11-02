package com.web.project.channel.vo;

/** 阿里云短信配置响应，不回传 AccessKey Secret。 */
public record AliyunSmsSettingsVO(
        String accessKeyId,
        String signName,
        String templateCode,
        boolean accessKeySecretConfigured,
        boolean configured
) {
}
