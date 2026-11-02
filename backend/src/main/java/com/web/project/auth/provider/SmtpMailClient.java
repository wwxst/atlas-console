package com.web.project.auth.provider;

import com.web.project.channel.model.EmailSmtpConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** 使用配置的 SMTP 服务器发送邮箱验证码。 */
@Component
public class SmtpMailClient {

    JavaMailSenderImpl createSender(EmailSmtpConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        sender.setProtocol("smtp");
        sender.setUsername(config.username());
        sender.setPassword(config.password());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
        if ("SSL".equals(config.security())) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else if ("STARTTLS".equals(config.security())) {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        }
        return sender;
    }

    public void send(EmailSmtpConfig config, String target, String code) {
        JavaMailSenderImpl sender = createSender(config);
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(config.fromAddress(), config.fromName());
            helper.setTo(target);
            helper.setSubject("Atlas 邮箱验证码");
            helper.setText("您的验证码为：" + code + "。验证码 5 分钟内有效，请勿向他人泄露。");
            sender.send(message);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new IllegalStateException("SMTP 邮件发送失败", exception);
        }
    }
}
