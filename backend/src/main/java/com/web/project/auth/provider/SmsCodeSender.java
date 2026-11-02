package com.web.project.auth.provider;

import com.web.project.channel.service.LoginProviderSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 根据当前登录配置选择阿里云或腾讯云发送手机验证码。 */
@Component
@RequiredArgsConstructor
public class SmsCodeSender {

    private final LoginProviderSettingsService settingsService;
    private final AliyunSmsClient aliyunSmsClient;
    private final TencentSmsClient tencentSmsClient;

    public void send(String target, String code) {
        String provider = settingsService.getSmsProvider();
        if ("aliyun".equals(provider)) {
            aliyunSmsClient.send(settingsService.getAliyunSmsConfigForSending(), target, code);
        } else {
            tencentSmsClient.send(settingsService.getTencentSmsConfigForSending(), target, code);
        }
    }
}
