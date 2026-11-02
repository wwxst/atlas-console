package com.web.project.auth.provider;

import com.web.project.channel.model.EmailSmtpConfig;
import com.web.project.channel.service.LoginProviderSettingsService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class EmailCodeSenderTest {

    @Test
    void sendsCodeUsingCurrentStoredSmtpConfiguration() {
        LoginProviderSettingsService settings = mock(LoginProviderSettingsService.class);
        SmtpMailClient mailClient = mock(SmtpMailClient.class);
        EmailSmtpConfig config = new EmailSmtpConfig(
                "smtp.example.com", 465, "sender@example.com", "smtp-secret",
                "no-reply@example.com", "Atlas", "SSL");
        when(settings.getEmailSmtpConfigForSending()).thenReturn(config);

        new EmailCodeSender(settings, mailClient).send("user@example.com", "123456");

        verify(settings).getEmailSmtpConfigForSending();
        verify(mailClient).send(config, "user@example.com", "123456");
    }
}
