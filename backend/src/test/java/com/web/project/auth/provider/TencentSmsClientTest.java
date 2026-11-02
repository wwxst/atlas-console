package com.web.project.auth.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.project.channel.model.TencentSmsConfig;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TencentSmsClientTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void signsTencentTc3RequestAndAcceptsSuccessfulStatus() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"Response\":{\"SendStatusSet\":[{\"Code\":\"Ok\"}]}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        TencentSmsClient client = new TencentSmsClient(http, new ObjectMapper());
        client.send(new TencentSmsConfig("secret-id", "secret-key", "1400000000",
                "Atlas", "100001", "ap-guangzhou"), "13800138000", "123456");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertEquals("https://sms.tencentcloudapi.com/", request.uri().toString());
        assertEquals("POST", request.method());
        assertTrue(request.headers().firstValue("Authorization").orElseThrow()
                .startsWith("TC3-HMAC-SHA256 Credential=secret-id/"));
        assertEquals("SendSms", request.headers().firstValue("X-TC-Action").orElseThrow());
        assertEquals("ap-guangzhou", request.headers().firstValue("X-TC-Region").orElseThrow());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void providerFailureDoesNotExposeResponseBody() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"Response\":{\"Error\":{\"Code\":\"AuthFailure\"}}}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new TencentSmsClient(http, new ObjectMapper()).send(
                        new TencentSmsConfig("id", "secret", "1400000000",
                                "Atlas", "100001", "ap-guangzhou"),
                        "13800138000", "123456"));

        assertFalse(failure.getMessage().contains("AuthFailure"));
    }
}
