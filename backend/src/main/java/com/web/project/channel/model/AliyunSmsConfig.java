package com.web.project.channel.model;

/** 已解密的阿里云短信发送配置，仅供服务端发送链路使用。 */
public record AliyunSmsConfig(
        String accessKeyId,
        String accessKeySecret,
        String signName,
        String templateCode
) {
    @Override
    public String toString() {
        return "AliyunSmsConfig[accessKeyId=redacted, accessKeySecret=redacted, signName=redacted, templateCode=redacted]";
    }
}
