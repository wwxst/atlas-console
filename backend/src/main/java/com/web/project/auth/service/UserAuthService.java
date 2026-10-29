package com.web.project.auth.service;

import com.web.project.auth.dto.RefreshTokenDTO;
import com.web.project.auth.dto.ResetPasswordDTO;
import com.web.project.auth.dto.SendCodeDTO;
import com.web.project.auth.dto.UserLoginDTO;
import com.web.project.auth.dto.UserRegisterDTO;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.TokenPairVO;

/**
 * 普通用户认证业务接口。
 */
public interface UserAuthService {

    /**
     * 发送验证码。
     *
     * @param dto           发送参数
     * @param currentUserId 当前登录用户 ID；仅换绑场景需要
     */
    void sendCode(SendCodeDTO dto, Long currentUserId);

    /**
     * 注册并返回双 Token。
     */
    TokenPairVO register(UserRegisterDTO registerDTO);

    /**
     * 密码或验证码登录。
     */
    TokenPairVO login(UserLoginDTO loginDTO);

    /**
     * 使用 Refresh Token 换取新的 Access Token。
     */
    AccessTokenVO refresh(RefreshTokenDTO refreshTokenDTO);

    /**
     * 删除当前会话。
     */
    void logout(String sessionId);

    /**
     * 忘记密码：校验验证码后重置密码。
     */
    void resetPassword(ResetPasswordDTO resetPasswordDTO);
}
