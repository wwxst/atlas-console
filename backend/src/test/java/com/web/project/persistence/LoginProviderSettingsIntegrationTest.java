package com.web.project.persistence;

import com.web.project.channel.dto.AliyunSmsSettingsUpdateDTO;
import com.web.project.channel.dto.EmailLoginSettingsUpdateDTO;
import com.web.project.channel.dto.LoginSettingsUpdateDTO;
import com.web.project.channel.dto.TencentSmsSettingsUpdateDTO;
import com.web.project.channel.service.LoginProviderSettingsService;
import com.web.project.common.exception.BusinessException;
import com.web.project.message.dto.SmsTemplateInputDTO;
import com.web.project.message.dto.SmsTemplateQueryDTO;
import com.web.project.message.service.SmsTemplateService;
import com.web.project.storage.service.CredentialCipher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ATLAS_POSTGRES_TEST_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(properties = {
        "spring.datasource.url=${ATLAS_POSTGRES_TEST_URL}",
        "spring.flyway.enabled=true",
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
        "app.storage.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@Transactional
class LoginProviderSettingsIntegrationTest {

    @Autowired
    private LoginProviderSettingsService settings;

    @Autowired
    private SmsTemplateService templates;

    @Autowired
    private CredentialCipher cipher;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void providerSecretsAreEncryptedPreservedAndNotReturned() {
        String aliyunSecret = "aliyun-integration-secret";
        var aliyun = settings.updateAliyunSmsSettings(
                new AliyunSmsSettingsUpdateDTO("aliyun-id", aliyunSecret, "Atlas", "SMS_100"));
        assertTrue(aliyun.configured());
        assertTrue(aliyun.accessKeySecretConfigured());
        assertFalse(aliyun.toString().contains(aliyunSecret));
        String encryptedAliyun = jdbc.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = ?",
                String.class, "auth.sms.aliyun.access-key-secret");
        assertNotEquals(aliyunSecret, encryptedAliyun);
        assertEquals(aliyunSecret, cipher.decrypt(encryptedAliyun));

        settings.updateAliyunSmsSettings(
                new AliyunSmsSettingsUpdateDTO("aliyun-id-2", "", "Atlas", "SMS_101"));
        assertEquals(encryptedAliyun, jdbc.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = ?",
                String.class, "auth.sms.aliyun.access-key-secret"));

        String tencentSecret = "tencent-integration-secret";
        var tencent = settings.updateTencentSmsSettings(new TencentSmsSettingsUpdateDTO(
                "tencent-id", tencentSecret, "1400000000", "Atlas", "100001", "ap-guangzhou"));
        assertTrue(tencent.configured());
        assertFalse(tencent.toString().contains(tencentSecret));
        assertEquals(tencentSecret, settings.getTencentSmsConfigForSending().secretKey());

        String smtpPassword = "smtp-integration-password";
        var email = settings.updateEmailLoginSettings(new EmailLoginSettingsUpdateDTO(
                "smtp.example.com", 465, "sender@example.com", smtpPassword,
                "no-reply@example.com", "Atlas", "SSL"));
        assertTrue(email.configured());
        assertFalse(email.toString().contains(smtpPassword));
        assertEquals(smtpPassword, settings.getEmailSmtpConfigForSending().password());
        assertNotEquals(smtpPassword, jdbc.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = ?",
                String.class, "auth.email.smtp.password"));
    }

    @Test
    void loginSettingsKeepAtLeastOneChannelEnabled() {
        var saved = settings.updateLoginSettings(new LoginSettingsUpdateDTO(true, false, "tencent"));
        assertTrue(saved.phoneLoginEnabled());
        assertFalse(saved.emailLoginEnabled());
        assertEquals("tencent", saved.smsProvider());
        assertEquals("false", jdbc.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = ?",
                String.class, "auth.email.enabled"));

        assertThrows(BusinessException.class,
                () -> settings.updateLoginSettings(new LoginSettingsUpdateDTO(false, false, "aliyun")));
        assertEquals("true", jdbc.queryForObject(
                "SELECT config_value FROM sys_config WHERE config_key = ?",
                String.class, "auth.phone.enabled"));
    }

    @Test
    void smsTemplateCrudPersistsJsonVariablesAndEnforcesProviderTemplateUniqueness() {
        String code = "SMS_INTEGRATION_" + System.nanoTime();
        var input = new SmsTemplateInputDTO("aliyun", "登录验证码", code, "verification",
                "验证码：${code}", List.of("code"), "approved", true);
        var created = templates.create(input);
        assertNotNull(created.id());
        assertEquals(List.of("code"), created.variables());

        var page = templates.page(new SmsTemplateQueryDTO(1, 20, null, "aliyun", "verification", "approved"));
        assertTrue(page.records().stream().anyMatch(item -> item.id().equals(created.id())));

        var updated = templates.update(created.id(), new SmsTemplateInputDTO(
                "aliyun", "登录验证码已更新", code, "verification", "验证码：${code}",
                List.of("code"), "approved", false));
        assertEquals("登录验证码已更新", updated.name());
        assertFalse(updated.enabled());

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sms_template WHERE id = ? AND variables_json = ?",
                Integer.class, created.id(), "[\"code\"]"));

        templates.delete(created.id());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM sms_template WHERE id = ?", Integer.class, created.id()));
    }
}
