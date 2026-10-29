package com.web.project.auth.controller;

import com.web.project.auth.dto.RefreshTokenDTO;
import com.web.project.auth.dto.SysUserLoginDTO;
import com.web.project.auth.service.SysUserAuthService;
import com.web.project.auth.protection.ClientIpResolver;
import com.web.project.auth.protection.LoginProtectionMetrics;
import com.web.project.auth.protection.LoginRateLimiter;
import com.web.project.auth.util.JwtClaims;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.SysUserInfoVO;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * 系统用户认证接口。
 *
 * 负责接收系统用户登录、刷新、退出和当前身份查询请求。
 */
@RestController
@RequestMapping("/api/sys-user/auth")
public class SysUserAuthController {

    private final SysUserAuthService sysUserAuthService;
    private final LoginRateLimiter loginRateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final LoginProtectionMetrics loginProtectionMetrics;

    @Autowired
    public SysUserAuthController(
            SysUserAuthService sysUserAuthService,
            LoginRateLimiter loginRateLimiter,
            LoginProtectionMetrics loginProtectionMetrics,
            ClientIpResolver clientIpResolver
    ) {
        this.sysUserAuthService = sysUserAuthService;
        this.loginRateLimiter = loginRateLimiter;
        this.loginProtectionMetrics = loginProtectionMetrics;
        this.clientIpResolver = clientIpResolver;
    }

    public SysUserAuthController(
            SysUserAuthService sysUserAuthService,
            LoginRateLimiter loginRateLimiter,
            LoginProtectionMetrics loginProtectionMetrics
    ) {
        this(sysUserAuthService, loginRateLimiter, loginProtectionMetrics, null);
    }

    /** standalone MVC 测试构造器；生产容器使用带保护组件的构造器。 */
    public SysUserAuthController(SysUserAuthService sysUserAuthService) {
        this(sysUserAuthService, null, null, null);
    }

    /**
     * 系统用户登录接口。
     */
    @PostMapping("/login")
    public Result<TokenPairVO> login(
            @Valid @RequestBody SysUserLoginDTO loginDTO,
            HttpServletRequest request
    ) {
        if (loginRateLimiter != null && !loginRateLimiter.allow(
                clientIpResolver == null ? request.getRemoteAddr() : clientIpResolver.resolve(request),
                "SYS:" + loginDTO.username().trim())) {
            throw new com.web.project.common.exception.BusinessException(
                    com.web.project.common.error.ErrorCode.LOGIN_RATE_LIMITED);
        }
        TokenPairVO tokenPair = sysUserAuthService.login(loginDTO);
        return Result.success(tokenPair);
    }

    /** 管理员查看登录密码校验聚合指标，不包含账号、IP、Token 或密码。 */
    @GetMapping("/login-metrics")
    public Result<LoginProtectionMetrics.Snapshot> loginMetrics() {
        return Result.success(loginProtectionMetrics.snapshot());
    }

    /**
     * 使用 Refresh Token 换取新的 Access Token。
     */
    @PostMapping("/refresh")
    public Result<AccessTokenVO> refresh(
            @Valid @RequestBody RefreshTokenDTO refreshTokenDTO
    ) {
        AccessTokenVO accessTokenVO = sysUserAuthService.refresh(refreshTokenDTO);
        return Result.success(accessTokenVO);
    }

    /**
     * 退出登录，删除当前会话。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        sysUserAuthService.logout(JwtClaims.sessionId(jwt));
        return Result.success();
    }

    /**
     * 当前系统用户。
     */
    @GetMapping("/me")
    public Result<SysUserInfoVO> getCurrentSysUser(@AuthenticationPrincipal Jwt jwt) {
        SysUserInfoVO sysUserInfoVO = sysUserAuthService.getCurrentSysUser(JwtClaims.accountId(jwt));

        return Result.success(sysUserInfoVO);
    }
}
