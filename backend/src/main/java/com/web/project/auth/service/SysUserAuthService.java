package com.web.project.auth.service;

import com.web.project.auth.dto.RefreshTokenDTO;
import com.web.project.auth.dto.SysUserLoginDTO;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.SysUserInfoVO;
import com.web.project.auth.vo.TokenPairVO;

/**
 * 系统用户认证业务接口。
 */
public interface SysUserAuthService {
    /**
     * 系统用户登录。
     *
     * @param loginDTO 前端提交的登录参数
     * @return 双 Token
     */
    TokenPairVO login(SysUserLoginDTO loginDTO);

    /**
     * 使用 Refresh Token 换取新的 Access Token。
     *
     * @param refreshTokenDTO Refresh Token 请求参数
     * @return 新的 Access Token
     */
    AccessTokenVO refresh(RefreshTokenDTO refreshTokenDTO);

    /**
     * 删除系统用户当前会话。
     *
     * @param sessionId Access Token 中的会话 ID
     */
    void logout(String sessionId);

    /**
     * 获取当前登录系统用户信息。
     *
     * @param sysUserId Token 中解析出的系统用户 ID
     * @return 当前系统用户信息
     */
    SysUserInfoVO getCurrentSysUser(Long sysUserId);
}
