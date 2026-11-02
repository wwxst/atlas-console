package com.web.project.auth.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.project.channel.model.AliyunSmsConfig;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AliyunSmsClientTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sendsSignedRpcRequestAndAcceptsSuccessfulProviderResponse() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"Code\":\"OK\",\"RequestId\":\"request-id\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        AliyunSmsClient client = new AliyunSmsClient(http, new ObjectMapper());
        client.send(new AliyunSmsConfig("access-id", "access-secret", "Atlas", "SMS_100"),
                "13800138000", "123456");

        var captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = captor.getValue();
        assertEquals("https://dysmsapi.aliyuncs.com/", request.uri().toString());
        assertEquals("POST", request.method());
        assertTrue(request.headers().firstValue("Content-Type").orElseThrow()
                .startsWith("application/x-www-form-urlencoded"));
        assertTrue(request.bodyPublisher().isPresent());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void providerFailureDoesNotExposeResponseBody() throws Exception {
        HttpClient http = mock(HttpClient.class);
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"Code\":\"SignatureDoesNotMatch\",\"Message\":\"secret detail\"}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new AliyunSmsClient(http, new ObjectMapper()).send(
                        new AliyunSmsConfig("access-id", "secret", "Atlas", "SMS_100"),
                        "13800138000", "123456"));

        assertFalse(failure.getMessage().contains("SignatureDoesNotMatch"));
        assertFalse(failure.getMessage().contains("secret detail"));
    }
}
