package com.web.project.channel.service;

import com.web.project.channel.dto.AliyunSmsSettingsUpdateDTO;
import com.web.project.channel.dto.EmailLoginSettingsUpdateDTO;
import com.web.project.channel.dto.LoginSettingsUpdateDTO;
import com.web.project.channel.dto.TencentSmsSettingsUpdateDTO;
import com.web.project.channel.entity.SysConfig;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.storage.service.CredentialCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginProviderSettingsServiceTest {

    private final SysConfigMapper mapper = mock(SysConfigMapper.class);
    private final CredentialCipher cipher = new CredentialCipher(
            Base64.getEncoder().encodeToString(new byte[32]));
    private final Map<String, String> values = new HashMap<>();
    private final LoginProviderSettingsService service =
            new LoginProviderSettingsService(mapper, cipher);

    @BeforeEach
    void configureMapper() {
        values.put("auth.phone.enabled", "true");
        values.put("auth.email.enabled", "true");
        values.put("auth.sms.provider", "aliyun");
        values.put("auth.sms.aliyun.access-key-id", "");
        values.put("auth.sms.aliyun.access-key-secret", "");
        values.put("auth.sms.aliyun.sign-name", "");
        values.put("auth.sms.aliyun.login-template-code", "");
        values.put("auth.sms.tencent.secret-id", "");
        values.put("auth.sms.tencent.secret-key", "");
        values.put("auth.sms.tencent.sdk-app-id", "");
        values.put("auth.sms.tencent.sign-name", "");
        values.put("auth.sms.tencent.login-template-id", "");
        values.put("auth.sms.tencent.region", "ap-guangzhou");
        values.put("auth.email.smtp.host", "");
        values.put("auth.email.smtp.port", "465");
        values.put("auth.email.smtp.username", "");
        values.put("auth.email.smtp.password", "");
        values.put("auth.email.smtp.from-address", "");
        values.put("auth.email.smtp.from-name", "Atlas");
        values.put("auth.email.smtp.security", "SSL");

        when(mapper.selectByKeys(anyList())).thenAnswer(invocation -> rows(invocation.getArgument(0)));
        when(mapper.selectByKeysForUpdate(anyList())).thenAnswer(invocation -> rows(invocation.getArgument(0)));
        when(mapper.selectMasterSwitchesForUpdate()).thenAnswer(
                invocation -> rows(List.of("auth.phone.enabled", "auth.email.enabled")));
        when(mapper.updateValue(anyString(), anyString())).thenAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return 1;
        });
    }

    @Test
    void loginSettingsReadUsesOneConsistentConfigSnapshot() {
        var settings = service.getLoginSettings();

        assertTrue(settings.phoneLoginEnabled());
        assertTrue(settings.emailLoginEnabled());
        assertEquals("aliyun", settings.smsProvider());
        verify(mapper).selectByKeys(List.of(
                "auth.phone.enabled", "auth.email.enabled", "auth.sms.provider"));
    }

    @Test
    void loginSettingsUpdatePersistsBothSwitchesAndProviderAtomically() {
        var saved = service.updateLoginSettings(new LoginSettingsUpdateDTO(true, false, "tencent"));

        assertTrue(saved.phoneLoginEnabled());
        assertFalse(saved.emailLoginEnabled());
        assertEquals("tencent", saved.smsProvider());
        assertEquals("true", values.get("auth.phone.enabled"));
        assertEquals("false", values.get("auth.email.enabled"));
        assertEquals("tencent", values.get("auth.sms.provider"));
        verify(mapper).selectMasterSwitchesForUpdate();
        verify(mapper).selectByKeysForUpdate(List.of("auth.sms.provider"));
    }

    @Test
    void loginSettingsCannotDisableBothChannels() {
        BusinessException failure = assertThrows(BusinessException.class,
                () -> service.updateLoginSettings(new LoginSettingsUpdateDTO(false, false, "aliyun")));

        assertEquals(ErrorCode.CANNOT_DISABLE_ALL_CHANNELS, failure.getErrorCode());
        verify(mapper, never()).updateValue(anyString(), anyString());
    }

    @Test
    void aliyunSecretIsEncryptedAndBlankUpdateKeepsExistingSecret() {
        String secret = "aliyun-test-secret";
        var first = service.updateAliyunSmsSettings(
                new AliyunSmsSettingsUpdateDTO("access-id", secret, "Atlas", "SMS_100"));
        assertTrue(first.configured());
        assertTrue(first.accessKeySecretConfigured());
        assertFalse(first.toString().contains(secret));

        String encrypted = values.get("auth.sms.aliyun.access-key-secret");
        assertNotEquals(secret, encrypted);
        assertEquals(secret, cipher.decrypt(encrypted));

        var second = service.updateAliyunSmsSettings(
                new AliyunSmsSettingsUpdateDTO("access-id-2", "", "Atlas", "SMS_101"));
        assertTrue(second.configured());
        assertEquals(encrypted, values.get("auth.sms.aliyun.access-key-secret"));
        assertEquals(secret, service.getAliyunSmsConfigForSending().accessKeySecret());
    }

    @Test
    void tencentSecretIsEncryptedAndNotReturned() {
        String secret = "tencent-test-secret";
        var saved = service.updateTencentSmsSettings(new TencentSmsSettingsUpdateDTO(
                "secret-id", secret, "1400000000", "Atlas", "100001", "ap-shanghai"));

        assertTrue(saved.configured());
        assertTrue(saved.secretKeyConfigured());
        assertFalse(saved.toString().contains(secret));
        assertEquals(secret, service.getTencentSmsConfigForSending().secretKey());
        assertNotEquals(secret, values.get("auth.sms.tencent.secret-key"));
    }

    @Test
    void smtpPasswordIsEncryptedAndBlankUpdateKeepsExistingPassword() {
        String password = "smtp-test-password";
        var first = service.updateEmailLoginSettings(new EmailLoginSettingsUpdateDTO(
                "smtp.example.com", 587, "sender@example.com", password,
                "no-reply@example.com", "Atlas", "STARTTLS"));

        assertTrue(first.configured());
        assertTrue(first.passwordConfigured());
        assertFalse(first.toString().contains(password));
        assertNotEquals(password, values.get("auth.email.smtp.password"));
        assertEquals(password, service.getEmailSmtpConfigForSending().password());

        String encrypted = values.get("auth.email.smtp.password");
        service.updateEmailLoginSettings(new EmailLoginSettingsUpdateDTO(
                "smtp.example.com", 587, "sender@example.com", "",
                "no-reply@example.com", "Atlas", "STARTTLS"));
        assertEquals(encrypted, values.get("auth.email.smtp.password"));
        assertEquals(password, service.getEmailSmtpConfigForSending().password());
    }

    @Test
    void unsupportedPersistedTencentRegionIsNotReportedAsConfigured() {
        values.put("auth.sms.tencent.secret-id", "secret-id");
        values.put("auth.sms.tencent.secret-key", cipher.encrypt("secret-key"));
        values.put("auth.sms.tencent.sdk-app-id", "1400000000");
        values.put("auth.sms.tencent.sign-name", "Atlas");
        values.put("auth.sms.tencent.login-template-id", "100001");
        values.put("auth.sms.tencent.region", "invalid-region");

        assertFalse(service.getTencentSmsSettings().configured());
        assertThrows(BusinessException.class, service::getTencentSmsConfigForSending);
    }

    @Test
    void invalidPersistedSmtpPortIsNotReportedAsConfigured() {
        values.put("auth.email.smtp.host", "smtp.example.com");
        values.put("auth.email.smtp.port", "65536");
        values.put("auth.email.smtp.username", "sender@example.com");
        values.put("auth.email.smtp.password", cipher.encrypt("smtp-password"));
        values.put("auth.email.smtp.from-address", "no-reply@example.com");
        values.put("auth.email.smtp.from-name", "Atlas");
        values.put("auth.email.smtp.security", "SSL");

        assertFalse(service.getEmailLoginSettings().configured());
        assertThrows(BusinessException.class, service::getEmailSmtpConfigForSending);
    }

    @Test
    void cannotSendWhenProviderIsNotConfigured() {
        BusinessException sms = assertThrows(BusinessException.class,
                service::getAliyunSmsConfigForSending);
        BusinessException email = assertThrows(BusinessException.class,
                service::getEmailSmtpConfigForSending);
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, sms.getErrorCode());
        assertEquals(ErrorCode.INTERNAL_SERVER_ERROR, email.getErrorCode());
    }

    private List<SysConfig> rows(List<String> keys) {
        return keys.stream().map(key -> {
            SysConfig config = new SysConfig();
            config.setConfigKey(key);
            config.setConfigValue(values.get(key));
            return config;
        }).toList();
    }
}
