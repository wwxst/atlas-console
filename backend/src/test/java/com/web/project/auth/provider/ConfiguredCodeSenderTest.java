package com.web.project.auth.provider;

import com.web.project.auth.code.ConfiguredCodeSender;
import com.web.project.auth.model.TargetType;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class ConfiguredCodeSenderTest {

    @Test
    void routesPhoneToSmsAndEmailToSmtp() {
        SmsCodeSender sms = mock(SmsCodeSender.class);
        EmailCodeSender email = mock(EmailCodeSender.class);
        ConfiguredCodeSender sender = new ConfiguredCodeSender(sms, email);

        sender.send(TargetType.PHONE, "13800138000", "123456");
        sender.send(TargetType.EMAIL, "user@example.com", "654321");

        verify(sms).send("13800138000", "123456");
        verify(email).send("user@example.com", "654321");
    }
}
