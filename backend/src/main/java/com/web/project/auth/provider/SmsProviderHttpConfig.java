package com.web.project.auth.provider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/** 短信服务商 HTTP 请求使用固定超时且不跟随重定向。 */
@Configuration
public class SmsProviderHttpConfig {

    @Bean
    HttpClient smsProviderHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
