package com.web.project.auth.controller;

import com.web.project.auth.dto.RefreshTokenDTO;
import com.web.project.auth.dto.UserLoginDTO;
import com.web.project.auth.model.TargetType;
import com.web.project.auth.protection.LoginProtectionMetrics;
import com.web.project.auth.protection.LoginRateLimiter;
import com.web.project.auth.service.SysUserAuthService;
import com.web.project.auth.service.UserAuthService;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginControllerProtectionTest {

    @Test
    void userLoginReturns429WithoutCallingServiceWhenRateLimited() {
        UserAuthService service = mock(UserAuthService.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        when(limiter.allow("127.0.0.1", "USER:EMAIL:user@example.com")).thenReturn(false);
        UserAuthController controller = new UserAuthController(service, limiter);
        HttpServletRequest request = request("127.0.0.1");

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.login(
                new UserLoginDTO("user@example.com", TargetType.EMAIL, "password123", null), request));

        assertEquals(ErrorCode.LOGIN_RATE_LIMITED, exception.getErrorCode());
        verifyNoInteractions(service);
    }

    @Test
    void refreshDoesNotUseLoginRateLimiter() {
        SysUserAuthService service = mock(SysUserAuthService.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        when(service.refresh(any())).thenReturn(new AccessTokenVO("access"));
        SysUserAuthController controller = new SysUserAuthController(
                service, limiter, new LoginProtectionMetrics());

        assertEquals("access", controller.refresh(new RefreshTokenDTO("refresh")).data().accessToken());
        verifyNoInteractions(limiter);
    }

    @Test
    void successfulLoginPassesThroughAfterRateLimitCheck() {
        UserAuthService service = mock(UserAuthService.class);
        LoginRateLimiter limiter = mock(LoginRateLimiter.class);
        when(limiter.allow("127.0.0.1", "USER:PHONE:13800138000")).thenReturn(true);
        when(service.login(any())).thenReturn(new TokenPairVO("access", "refresh"));
        UserAuthController controller = new UserAuthController(service, limiter);

        assertEquals("access", controller.login(
                new UserLoginDTO("13800138000", TargetType.PHONE, "password123", null),
                request("127.0.0.1")).data().accessToken());
        verify(service).login(any());
    }

    @Test
    void rateLimitBusinessErrorUsesHttp429() {
        var response = new GlobalExceptionHandler().handleBusinessException(
                new BusinessException(ErrorCode.LOGIN_RATE_LIMITED));

        assertEquals(429, response.getStatusCode().value());
        assertEquals(42901, response.getBody().code());
    }

    private HttpServletRequest request(String ip) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(ip);
        return request;
    }
}
