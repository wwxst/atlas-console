package com.web.project.config.security;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证请求统一返回 HTTP 401 与业务错误码，不暴露具体 Token 校验原因。
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Result<Void> result = Result.error(
                ErrorCode.LOGIN_STATUS_INVALID.getCode(),
                ErrorCode.LOGIN_STATUS_INVALID.getDefaultMessage()
        );

        objectMapper.writeValue(response.getOutputStream(), result);
    }
}
