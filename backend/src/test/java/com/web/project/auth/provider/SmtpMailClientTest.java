package com.web.project.auth.provider;

import com.web.project.channel.model.EmailSmtpConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.junit.jupiter.api.Assertions.*;

class SmtpMailClientTest {

    private final SmtpMailClient client = new SmtpMailClient();

    @Test
    void sslUsesEncryptedSmtpWithBoundedTimeouts() {
        JavaMailSenderImpl sender = client.createSender(config("SSL"));
        var properties = sender.getJavaMailProperties();

        assertEquals("smtp.example.com", sender.getHost());
        assertEquals(465, sender.getPort());
        assertEquals("sender@example.com", sender.getUsername());
        assertEquals("smtp-password", sender.getPassword());
        assertEquals("true", properties.getProperty("mail.smtp.auth"));
        assertEquals("true", properties.getProperty("mail.smtp.ssl.enable"));
        assertEquals("5000", properties.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("5000", properties.getProperty("mail.smtp.timeout"));
        assertEquals("5000", properties.getProperty("mail.smtp.writetimeout"));
    }

    @Test
    void startTlsRequiresUpgradeBeforeAuthentication() {
        var properties = client.createSender(config("STARTTLS")).getJavaMailProperties();
        assertEquals("true", properties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", properties.getProperty("mail.smtp.starttls.required"));
        assertNull(properties.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void noneDoesNotEnableTlsOrSsl() {
        var properties = client.createSender(config("NONE")).getJavaMailProperties();
        assertNull(properties.getProperty("mail.smtp.starttls.enable"));
        assertNull(properties.getProperty("mail.smtp.ssl.enable"));
    }

    private EmailSmtpConfig config(String security) {
        return new EmailSmtpConfig("smtp.example.com", 465, "sender@example.com",
                "smtp-password", "no-reply@example.com", "Atlas", security);
    }
}
