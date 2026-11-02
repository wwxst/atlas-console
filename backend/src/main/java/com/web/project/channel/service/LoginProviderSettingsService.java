package com.web.project.channel.service;

import com.web.project.channel.dto.AliyunSmsSettingsUpdateDTO;
import com.web.project.channel.dto.EmailLoginSettingsUpdateDTO;
import com.web.project.channel.dto.LoginSettingsUpdateDTO;
import com.web.project.channel.dto.TencentSmsSettingsUpdateDTO;
import com.web.project.channel.entity.SysConfig;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.channel.model.AliyunSmsConfig;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.channel.model.EmailSmtpConfig;
import com.web.project.channel.model.TencentSmsConfig;
import com.web.project.channel.vo.AliyunSmsSettingsVO;
import com.web.project.channel.vo.EmailLoginSettingsVO;
import com.web.project.channel.vo.LoginSettingsVO;
import com.web.project.channel.vo.TencentSmsSettingsVO;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.storage.service.CredentialCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 登录邮件与短信服务商配置。密钥只在发送链路中解密，管理端响应不返回明文。 */
@Service
@RequiredArgsConstructor
public class LoginProviderSettingsService {

    private static final String PHONE_ENABLED = "auth.phone.enabled";
    private static final String EMAIL_ENABLED = "auth.email.enabled";
    private static final String SMS_PROVIDER = "auth.sms.provider";

    private static final String ALIYUN_ACCESS_KEY_ID = "auth.sms.aliyun.access-key-id";
    private static final String ALIYUN_ACCESS_KEY_SECRET = "auth.sms.aliyun.access-key-secret";
    private static final String ALIYUN_SIGN_NAME = "auth.sms.aliyun.sign-name";
    private static final String ALIYUN_TEMPLATE_CODE = "auth.sms.aliyun.login-template-code";

    private static final String TENCENT_SECRET_ID = "auth.sms.tencent.secret-id";
    private static final String TENCENT_SECRET_KEY = "auth.sms.tencent.secret-key";
    private static final String TENCENT_APP_ID = "auth.sms.tencent.sdk-app-id";
    private static final String TENCENT_SIGN_NAME = "auth.sms.tencent.sign-name";
    private static final String TENCENT_TEMPLATE_ID = "auth.sms.tencent.login-template-id";
    private static final String TENCENT_REGION = "auth.sms.tencent.region";

    private static final String EMAIL_HOST = "auth.email.smtp.host";
    private static final String EMAIL_PORT = "auth.email.smtp.port";
    private static final String EMAIL_USERNAME = "auth.email.smtp.username";
    private static final String EMAIL_PASSWORD = "auth.email.smtp.password";
    private static final String EMAIL_FROM_ADDRESS = "auth.email.smtp.from-address";
    private static final String EMAIL_FROM_NAME = "auth.email.smtp.from-name";
    private static final String EMAIL_SECURITY = "auth.email.smtp.security";

    private static final List<String> ALIYUN_KEYS = List.of(
            ALIYUN_ACCESS_KEY_ID, ALIYUN_ACCESS_KEY_SECRET, ALIYUN_SIGN_NAME, ALIYUN_TEMPLATE_CODE);
    private static final List<String> TENCENT_KEYS = List.of(
            TENCENT_SECRET_ID, TENCENT_SECRET_KEY, TENCENT_APP_ID,
            TENCENT_SIGN_NAME, TENCENT_TEMPLATE_ID, TENCENT_REGION);
    private static final List<String> EMAIL_KEYS = List.of(
            EMAIL_HOST, EMAIL_PORT, EMAIL_USERNAME, EMAIL_PASSWORD,
            EMAIL_FROM_ADDRESS, EMAIL_FROM_NAME, EMAIL_SECURITY);

    private final SysConfigMapper sysConfigMapper;
    private final AuthChannelService authChannelService;
    private final CredentialCipher credentialCipher;

    public LoginSettingsVO getLoginSettings() {
        AuthChannelConfig channels = authChannelService.getChannels();
        String provider = required(loadValues(List.of(SMS_PROVIDER)), SMS_PROVIDER);
        requireSmsProvider(provider);
        return new LoginSettingsVO(channels.phoneEnabled(), channels.emailEnabled(), provider);
    }

    @Transactional
    public LoginSettingsVO updateLoginSettings(LoginSettingsUpdateDTO dto) {
        Map<String, String> switches = sysConfigMapper.selectMasterSwitchesForUpdate().stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
        required(switches, PHONE_ENABLED);
        required(switches, EMAIL_ENABLED);
        // Lock the provider row only after the two master switches, matching the existing channel-update lock order.
        required(loadValuesForUpdate(List.of(SMS_PROVIDER)), SMS_PROVIDER);
        if (!dto.phoneLoginEnabled() && !dto.emailLoginEnabled()) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_ALL_CHANNELS);
        }
        write(PHONE_ENABLED, dto.phoneLoginEnabled().toString());
        write(EMAIL_ENABLED, dto.emailLoginEnabled().toString());
        write(SMS_PROVIDER, dto.smsProvider());
        return new LoginSettingsVO(dto.phoneLoginEnabled(), dto.emailLoginEnabled(), dto.smsProvider());
    }

    public AliyunSmsSettingsVO getAliyunSmsSettings() {
        Map<String, String> values = loadValues(ALIYUN_KEYS);
        String encryptedSecret = required(values, ALIYUN_ACCESS_KEY_SECRET);
        String accessKeyId = required(values, ALIYUN_ACCESS_KEY_ID);
        String signName = required(values, ALIYUN_SIGN_NAME);
        String templateCode = required(values, ALIYUN_TEMPLATE_CODE);
        boolean secretConfigured = !encryptedSecret.isBlank();
        return new AliyunSmsSettingsVO(
                accessKeyId, signName, templateCode, secretConfigured,
                secretConfigured && !accessKeyId.isBlank() && !signName.isBlank() && !templateCode.isBlank());
    }

    @Transactional
    public AliyunSmsSettingsVO updateAliyunSmsSettings(AliyunSmsSettingsUpdateDTO dto) {
        Map<String, String> values = loadValuesForUpdate(ALIYUN_KEYS);
        String encryptedSecret = required(values, ALIYUN_ACCESS_KEY_SECRET);
        if (!dto.accessKeySecret().isBlank()) {
            encryptedSecret = credentialCipher.encrypt(dto.accessKeySecret());
        }
        if (encryptedSecret.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写阿里云 AccessKey Secret");
        }
        write(ALIYUN_ACCESS_KEY_ID, dto.accessKeyId().trim());
        write(ALIYUN_ACCESS_KEY_SECRET, encryptedSecret);
        write(ALIYUN_SIGN_NAME, dto.signName().trim());
        write(ALIYUN_TEMPLATE_CODE, dto.templateCode().trim());
        return getAliyunSmsSettings();
    }

    public TencentSmsSettingsVO getTencentSmsSettings() {
        Map<String, String> values = loadValues(TENCENT_KEYS);
        String secretKey = required(values, TENCENT_SECRET_KEY);
        String secretId = required(values, TENCENT_SECRET_ID);
        String appId = required(values, TENCENT_APP_ID);
        String signName = required(values, TENCENT_SIGN_NAME);
        String templateId = required(values, TENCENT_TEMPLATE_ID);
        String region = required(values, TENCENT_REGION);
        boolean secretConfigured = !secretKey.isBlank();
        return new TencentSmsSettingsVO(
                secretId, appId, signName, templateId, region, secretConfigured,
                secretConfigured && !secretId.isBlank() && !appId.isBlank()
                        && !signName.isBlank() && !templateId.isBlank() && !region.isBlank());
    }

    @Transactional
    public TencentSmsSettingsVO updateTencentSmsSettings(TencentSmsSettingsUpdateDTO dto) {
        Map<String, String> values = loadValuesForUpdate(TENCENT_KEYS);
        String encryptedSecret = required(values, TENCENT_SECRET_KEY);
        if (!dto.secretKey().isBlank()) {
            encryptedSecret = credentialCipher.encrypt(dto.secretKey());
        }
        if (encryptedSecret.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写腾讯云 SecretKey");
        }
        write(TENCENT_SECRET_ID, dto.secretId().trim());
        write(TENCENT_SECRET_KEY, encryptedSecret);
        write(TENCENT_APP_ID, dto.sdkAppId().trim());
        write(TENCENT_SIGN_NAME, dto.signName().trim());
        write(TENCENT_TEMPLATE_ID, dto.templateId().trim());
        write(TENCENT_REGION, dto.region());
        return getTencentSmsSettings();
    }

    public EmailLoginSettingsVO getEmailLoginSettings() {
        Map<String, String> values = loadValues(EMAIL_KEYS);
        String encryptedPassword = required(values, EMAIL_PASSWORD);
        String host = required(values, EMAIL_HOST);
        int port = Integer.parseInt(required(values, EMAIL_PORT));
        String username = required(values, EMAIL_USERNAME);
        String fromAddress = required(values, EMAIL_FROM_ADDRESS);
        String fromName = required(values, EMAIL_FROM_NAME);
        String security = required(values, EMAIL_SECURITY);
        boolean passwordConfigured = !encryptedPassword.isBlank();
        boolean configured = passwordConfigured && !host.isBlank() && port > 0
                && !username.isBlank() && !fromAddress.isBlank() && !fromName.isBlank()
                && List.of("SSL", "STARTTLS", "NONE").contains(security);
        return new EmailLoginSettingsVO(host, port, username, fromAddress, fromName,
                security, passwordConfigured, configured);
    }

    @Transactional
    public EmailLoginSettingsVO updateEmailLoginSettings(EmailLoginSettingsUpdateDTO dto) {
        Map<String, String> values = loadValuesForUpdate(EMAIL_KEYS);
        String encryptedPassword = required(values, EMAIL_PASSWORD);
        if (!dto.password().isBlank()) {
            encryptedPassword = credentialCipher.encrypt(dto.password());
        }
        if (encryptedPassword.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请填写 SMTP 密码或授权码");
        }
        write(EMAIL_HOST, dto.host().trim());
        write(EMAIL_PORT, dto.port().toString());
        write(EMAIL_USERNAME, dto.username().trim());
        write(EMAIL_PASSWORD, encryptedPassword);
        write(EMAIL_FROM_ADDRESS, dto.fromAddress().trim().toLowerCase(java.util.Locale.ROOT));
        write(EMAIL_FROM_NAME, dto.fromName().trim());
        write(EMAIL_SECURITY, dto.security());
        return getEmailLoginSettings();
    }

    public String getSmsProvider() {
        String provider = required(loadValues(List.of(SMS_PROVIDER)), SMS_PROVIDER);
        requireSmsProvider(provider);
        return provider;
    }

    public AliyunSmsConfig getAliyunSmsConfigForSending() {
        Map<String, String> values = loadValues(ALIYUN_KEYS);
        String encrypted = required(values, ALIYUN_ACCESS_KEY_SECRET);
        if (encrypted.isBlank() || required(values, ALIYUN_ACCESS_KEY_ID).isBlank()
                || required(values, ALIYUN_SIGN_NAME).isBlank()
                || required(values, ALIYUN_TEMPLATE_CODE).isBlank()) {
            throw notConfigured("阿里云短信");
        }
        return new AliyunSmsConfig(
                required(values, ALIYUN_ACCESS_KEY_ID),
                credentialCipher.decrypt(encrypted),
                required(values, ALIYUN_SIGN_NAME),
                required(values, ALIYUN_TEMPLATE_CODE));
    }

    public TencentSmsConfig getTencentSmsConfigForSending() {
        Map<String, String> values = loadValues(TENCENT_KEYS);
        String encrypted = required(values, TENCENT_SECRET_KEY);
        if (encrypted.isBlank() || required(values, TENCENT_SECRET_ID).isBlank()
                || required(values, TENCENT_APP_ID).isBlank()
                || required(values, TENCENT_SIGN_NAME).isBlank()
                || required(values, TENCENT_TEMPLATE_ID).isBlank()
                || required(values, TENCENT_REGION).isBlank()) {
            throw notConfigured("腾讯云短信");
        }
        return new TencentSmsConfig(
                required(values, TENCENT_SECRET_ID),
                credentialCipher.decrypt(encrypted),
                required(values, TENCENT_APP_ID),
                required(values, TENCENT_SIGN_NAME),
                required(values, TENCENT_TEMPLATE_ID),
                required(values, TENCENT_REGION));
    }

    public EmailSmtpConfig getEmailSmtpConfigForSending() {
        Map<String, String> values = loadValues(EMAIL_KEYS);
        String encrypted = required(values, EMAIL_PASSWORD);
        String security = required(values, EMAIL_SECURITY);
        String host = required(values, EMAIL_HOST);
        String portValue = required(values, EMAIL_PORT);
        String username = required(values, EMAIL_USERNAME);
        String fromAddress = required(values, EMAIL_FROM_ADDRESS);
        String fromName = required(values, EMAIL_FROM_NAME);
        if (encrypted.isBlank() || host.isBlank() || portValue.isBlank() || username.isBlank()
                || fromAddress.isBlank() || fromName.isBlank()
                || !List.of("SSL", "STARTTLS", "NONE").contains(security)) {
            throw notConfigured("SMTP 邮箱");
        }
        return new EmailSmtpConfig(host, Integer.parseInt(portValue), username,
                credentialCipher.decrypt(encrypted), fromAddress, fromName, security);
    }

    private Map<String, String> loadValues(List<String> keys) {
        return sysConfigMapper.selectByKeys(keys).stream().collect(Collectors.toMap(
                SysConfig::getConfigKey, SysConfig::getConfigValue));
    }

    private Map<String, String> loadValuesForUpdate(List<String> keys) {
        return sysConfigMapper.selectByKeysForUpdate(keys).stream().collect(Collectors.toMap(
                SysConfig::getConfigKey, SysConfig::getConfigValue));
    }

    private String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("登录服务配置缺失：" + key);
        }
        return value;
    }

    private void write(String key, String value) {
        if (sysConfigMapper.updateValue(key, value) != 1) {
            throw new IllegalStateException("登录服务配置未写入预期记录：" + key);
        }
    }

    private void requireSmsProvider(String provider) {
        if (!"aliyun".equals(provider) && !"tencent".equals(provider)) {
            throw new IllegalStateException("短信服务商配置缺失或格式错误");
        }
    }

    private BusinessException notConfigured(String provider) {
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, provider + "服务尚未配置，请联系管理员");
    }
}
