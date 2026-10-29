package com.web.project.user.controller;

import com.web.project.auth.dto.ResetPasswordDTO;
import com.web.project.auth.util.JwtClaims;
import com.web.project.common.result.Result;
import com.web.project.user.dto.RebindEmailDTO;
import com.web.project.user.dto.RebindPhoneDTO;
import com.web.project.user.dto.UpdateProfileDTO;
import com.web.project.user.service.UserProfileService;
import com.web.project.user.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户个人资料接口。
 *
 * 当前用户 ID 只从 JWT 的 sub 读取，不接受客户端传入用户 ID。
 * 系统用户管理端接口仍由 UserAccountController 负责。
 */
@RestController
@RequestMapping("/api/user/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 查询个人资料。
     */
    @GetMapping
    public Result<UserInfoVO> getProfile(@AuthenticationPrincipal Jwt jwt) {
        return Result.success(
                userProfileService.getProfile(JwtClaims.accountId(jwt))
        );
    }

    /**
     * 修改昵称和头像。
     */
    @PutMapping
    public Result<Void> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileDTO updateProfileDTO
    ) {
        userProfileService.updateProfile(
                JwtClaims.accountId(jwt),
                updateProfileDTO
        );

        return Result.success();
    }

    /**
     * 登录态修改密码。
     */
    @PutMapping("/password")
    public Result<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ResetPasswordDTO resetPasswordDTO
    ) {
        userProfileService.changePassword(
                JwtClaims.accountId(jwt),
                resetPasswordDTO
        );

        return Result.success();
    }

    /**
     * 换绑手机号。
     */
    @PutMapping("/phone")
    public Result<Void> rebindPhone(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RebindPhoneDTO rebindPhoneDTO
    ) {
        userProfileService.rebindPhone(
                JwtClaims.accountId(jwt),
                rebindPhoneDTO
        );

        return Result.success();
    }

    /**
     * 换绑邮箱。
     */
    @PutMapping("/email")
    public Result<Void> rebindEmail(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RebindEmailDTO rebindEmailDTO
    ) {
        userProfileService.rebindEmail(
                JwtClaims.accountId(jwt),
                rebindEmailDTO
        );

        return Result.success();
    }
}
