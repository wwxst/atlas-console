package com.web.project.auth.provider;

import com.web.project.channel.model.EmailSmtpConfig;
import com.web.project.channel.service.LoginProviderSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 读取最新 SMTP 配置并发送邮箱验证码。 */
@Component
@RequiredArgsConstructor
public class EmailCodeSender {

    private final LoginProviderSettingsService settingsService;
    private final SmtpMailClient mailClient;

    public void send(String target, String code) {
        EmailSmtpConfig config = settingsService.getEmailSmtpConfigForSending();
        mailClient.send(config, target, code);
    }
}
