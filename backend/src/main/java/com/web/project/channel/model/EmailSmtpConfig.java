package com.web.project.channel.model;

/** 已解密的 SMTP 发送配置，仅供服务端发送链路使用。 */
public record EmailSmtpConfig(
        String host,
        int port,
        String username,
        String password,
        String fromAddress,
        String fromName,
        String security
) {
    @Override
    public String toString() {
        return "EmailSmtpConfig[host=redacted, port=" + port
                + ", username=redacted, password=redacted, fromAddress=redacted, fromName=redacted, security=redacted]";
    }
}
