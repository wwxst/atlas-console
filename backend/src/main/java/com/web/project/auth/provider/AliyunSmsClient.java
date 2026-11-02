package com.web.project.auth.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.project.channel.model.AliyunSmsConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 阿里云 Dysmsapi 2017-05-25 SendSms RPC 客户端。 */
@Component
@RequiredArgsConstructor
public class AliyunSmsClient {

    private static final URI ENDPOINT = URI.create("https://dysmsapi.aliyuncs.com/");
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public void send(AliyunSmsConfig config, String phone, String code) {
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("AccessKeyId", config.accessKeyId());
        parameters.put("Action", "SendSms");
        parameters.put("Format", "JSON");
        parameters.put("PhoneNumbers", phone);
        parameters.put("RegionId", "cn-hangzhou");
        parameters.put("SignatureMethod", "HMAC-SHA1");
        parameters.put("SignatureNonce", UUID.randomUUID().toString());
        parameters.put("SignatureVersion", "1.0");
        parameters.put("Timestamp", TIMESTAMP.format(Instant.now()));
        parameters.put("Version", "2017-05-25");
        parameters.put("SignName", config.signName());
        parameters.put("TemplateCode", config.templateCode());
        parameters.put("TemplateParam", "{\"code\":\"" + code + "\"}");

        String canonicalQuery = encodeParameters(parameters);
        String stringToSign = "POST&%2F&" + encode(canonicalQuery);
        parameters.put("Signature", sign(config.accessKeySecret(), stringToSign));

        HttpRequest request = HttpRequest.newBuilder(ENDPOINT)
                .timeout(java.time.Duration.ofSeconds(5))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(
                        encodeParameters(parameters), StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IllegalStateException("阿里云短信服务请求失败");
            }
            JsonNode result = objectMapper.readTree(response.body());
            if (!"OK".equals(result.path("Code").asText())) {
                throw new IllegalStateException("阿里云短信发送失败");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("阿里云短信请求被中断", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("阿里云短信服务请求失败", exception);
        }
    }

    private String sign(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec((secret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("阿里云短信签名生成失败", exception);
        }
    }

    private String encodeParameters(Map<String, String> parameters) {
        return parameters.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}
