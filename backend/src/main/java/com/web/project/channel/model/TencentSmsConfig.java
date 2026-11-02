package com.web.project.channel.model;

/** 已解密的腾讯云短信发送配置，仅供服务端发送链路使用。 */
public record TencentSmsConfig(
        String secretId,
        String secretKey,
        String sdkAppId,
        String signName,
        String templateId,
        String region
) {
    @Override
    public String toString() {
        return "TencentSmsConfig[secretId=redacted, secretKey=redacted, sdkAppId=redacted, signName=redacted, templateId=redacted, region=redacted]";
    }
}
