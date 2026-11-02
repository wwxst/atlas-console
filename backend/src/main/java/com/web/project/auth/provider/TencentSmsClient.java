package com.web.project.auth.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.project.channel.model.TencentSmsConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 腾讯云 SMS v2021-01-11 SendSms TC3-HMAC-SHA256 客户端。 */
@Component
@RequiredArgsConstructor
public class TencentSmsClient {

    private static final String HOST = "sms.tencentcloudapi.com";
    private static final String SERVICE = "sms";
    private static final String VERSION = "2021-01-11";
    private static final String ACTION = "SendSms";
    private static final String CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String SIGNED_HEADERS = "content-type;host";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public void send(TencentSmsConfig config, String phone, String code) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("PhoneNumberSet", List.of("+86" + phone));
        payload.put("SmsSdkAppId", config.sdkAppId());
        payload.put("SignName", config.signName());
        payload.put("TemplateId", config.templateId());
        payload.put("TemplateParamSet", List.of(code));

        try {
            String body = objectMapper.writeValueAsString(payload);
            long timestamp = Instant.now().getEpochSecond();
            String date = DATE.format(Instant.ofEpochSecond(timestamp));
            String scope = date + "/" + SERVICE + "/tc3_request";
            String canonicalHeaders = "content-type:" + CONTENT_TYPE + "\n"
                    + "host:" + HOST + "\n";
            String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n"
                    + SIGNED_HEADERS + "\n" + sha256(body);
            String stringToSign = "TC3-HMAC-SHA256\n" + timestamp + "\n"
                    + scope + "\n" + sha256(canonicalRequest);
            byte[] secretDate = hmac(("TC3" + config.secretKey()).getBytes(StandardCharsets.UTF_8), date);
            byte[] secretService = hmac(secretDate, SERVICE);
            byte[] secretSigning = hmac(secretService, "tc3_request");
            String signature = toHex(hmac(secretSigning, stringToSign));
            String authorization = "TC3-HMAC-SHA256 Credential=" + config.secretId() + "/" + scope
                    + ", SignedHeaders=" + SIGNED_HEADERS + ", Signature=" + signature;

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://" + HOST + "/"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .header("Authorization", authorization)
                    .header("Content-Type", CONTENT_TYPE)
                    .header("X-TC-Action", ACTION)
                    .header("X-TC-Version", VERSION)
                    .header("X-TC-Timestamp", String.valueOf(timestamp))
                    .header("X-TC-Region", config.region())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("腾讯云短信服务请求失败");
            }
            JsonNode result = objectMapper.readTree(response.body()).path("Response");
            if (!result.path("Error").isMissingNode() && !result.path("Error").isNull()) {
                throw new IllegalStateException("腾讯云短信发送失败");
            }
            if (!"Ok".equals(result.path("SendStatusSet").path(0).path("Code").asText())) {
                throw new IllegalStateException("腾讯云短信发送失败");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("腾讯云短信请求被中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("腾讯云短信服务请求失败", exception);
        }
    }

    private byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("腾讯云短信签名生成失败", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("腾讯云短信摘要生成失败", exception);
        }
    }

    private String toHex(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
