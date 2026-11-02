package com.web.project.auth.provider;

import com.web.project.channel.model.AliyunSmsConfig;
import com.web.project.channel.model.TencentSmsConfig;
import com.web.project.channel.service.LoginProviderSettingsService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class SmsCodeSenderTest {

    @Test
    void selectsAliyunWhenConfigured() {
        LoginProviderSettingsService settings = mock(LoginProviderSettingsService.class);
        AliyunSmsClient aliyun = mock(AliyunSmsClient.class);
        TencentSmsClient tencent = mock(TencentSmsClient.class);
        AliyunSmsConfig config = new AliyunSmsConfig("id", "secret", "Atlas", "SMS_100");
        when(settings.getSmsProvider()).thenReturn("aliyun");
        when(settings.getAliyunSmsConfigForSending()).thenReturn(config);

        new SmsCodeSender(settings, aliyun, tencent).send("13800138000", "123456");

        verify(aliyun).send(config, "13800138000", "123456");
        verifyNoInteractions(tencent);
    }

    @Test
    void selectsTencentWhenConfigured() {
        LoginProviderSettingsService settings = mock(LoginProviderSettingsService.class);
        AliyunSmsClient aliyun = mock(AliyunSmsClient.class);
        TencentSmsClient tencent = mock(TencentSmsClient.class);
        TencentSmsConfig config = new TencentSmsConfig(
                "id", "secret", "1400000000", "Atlas", "100001", "ap-guangzhou");
        when(settings.getSmsProvider()).thenReturn("tencent");
        when(settings.getTencentSmsConfigForSending()).thenReturn(config);

        new SmsCodeSender(settings, aliyun, tencent).send("13800138000", "654321");

        verify(tencent).send(config, "13800138000", "654321");
        verifyNoInteractions(aliyun);
    }
}
