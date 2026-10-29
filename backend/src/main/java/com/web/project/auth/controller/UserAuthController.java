package com.web.project.auth.controller;

import com.web.project.auth.dto.RefreshTokenDTO;
import com.web.project.auth.dto.ResetPasswordDTO;
import com.web.project.auth.dto.SendCodeDTO;
import com.web.project.auth.dto.UserLoginDTO;
import com.web.project.auth.dto.UserRegisterDTO;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.service.UserAuthService;
import com.web.project.auth.protection.ClientIpResolver;
import com.web.project.auth.protection.LoginRateLimiter;
import com.web.project.auth.util.ContactNormalizer;
import com.web.project.auth.util.JwtClaims;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户认证接口。
 *
 * 注册、登录、刷新、发送验证码和重置密码允许匿名访问；
 * 换绑场景共用发送验证码接口，但必须携带有效的普通用户 Access Token。
 */
@RestController
@RequestMapping("/api/user/auth")
public class UserAuthController {

    private final UserAuthService userAuthService;
    private final LoginRateLimiter loginRateLimiter;
    private final ClientIpResolver clientIpResolver;

    @Autowired
    public UserAuthController(
            UserAuthService userAuthService,
            LoginRateLimiter loginRateLimiter,
            ClientIpResolver clientIpResolver
    ) {
        this.userAuthService = userAuthService;
        this.loginRateLimiter = loginRateLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    public UserAuthController(UserAuthService userAuthService, LoginRateLimiter loginRateLimiter) {
        this(userAuthService, loginRateLimiter, null);
    }

    /** standalone MVC 测试构造器；生产容器使用带限流器的构造器。 */
    public UserAuthController(UserAuthService userAuthService) {
        this(userAuthService, null, null);
    }

    /**
     * 发送验证码。
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(
            @Valid @RequestBody SendCodeDTO sendCodeDTO,
            @AuthenticationPrincipal Jwt jwt
    ) {
        userAuthService.sendCode(sendCodeDTO, currentUserId(jwt));
        return Result.success();
    }

    /**
     * 注册并返回双 Token。
     */
    @PostMapping("/register")
    public Result<TokenPairVO> register(
            @Valid @RequestBody UserRegisterDTO registerDTO
    ) {
        return Result.success(userAuthService.register(registerDTO));
    }

    /**
     * 密码或验证码登录。
     */
    @PostMapping("/login")
    public Result<TokenPairVO> login(
            @Valid @RequestBody UserLoginDTO loginDTO,
            HttpServletRequest request
    ) {
        if (loginRateLimiter != null && !loginRateLimiter.allow(
                clientIpResolver == null ? request.getRemoteAddr() : clientIpResolver.resolve(request),
                "USER:" + loginDTO.targetType().name() + ":"
                        + ContactNormalizer.normalize(loginDTO.targetType(), loginDTO.target()))) {
            throw new com.web.project.common.exception.BusinessException(
                    com.web.project.common.error.ErrorCode.LOGIN_RATE_LIMITED);
        }
        return Result.success(userAuthService.login(loginDTO));
    }

    /**
     * 使用 Refresh Token 换取新的 Access Token。
     */
    @PostMapping("/refresh")
    public Result<AccessTokenVO> refresh(
            @Valid @RequestBody RefreshTokenDTO refreshTokenDTO
    ) {
        return Result.success(userAuthService.refresh(refreshTokenDTO));
    }

    /**
     * 退出登录，删除当前会话。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        userAuthService.logout(JwtClaims.sessionId(jwt));
        return Result.success();
    }

    /**
     * 忘记密码。
     */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(
            @Valid @RequestBody ResetPasswordDTO resetPasswordDTO
    ) {
        userAuthService.resetPassword(resetPasswordDTO);
        return Result.success();
    }

    /**
     * 读取当前登录的普通用户 ID。
     *
     * 发送验证码接口允许匿名访问，只有携带普通用户 Access Token 时才返回用户 ID，
     * 供换绑场景校验目标归属。
     */
    private Long currentUserId(Jwt jwt) {
        if (jwt == null
                || !AccountType.USER.name().equals(
                        jwt.getClaimAsString("accountType")
                )) {
            return null;
        }

        return JwtClaims.accountId(jwt);
    }
}
