package com.web.project.auth.service.impl;

import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.auth.dto.RefreshTokenDTO;
import com.web.project.auth.dto.SysUserLoginDTO;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.protection.PasswordVerificationGuard;
import com.web.project.auth.service.AuthSessionService;
import com.web.project.auth.service.SysUserAuthService;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.SysUserInfoVO;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


/**
 * 系统用户认证业务实现类。
 *
 * 负责校验用户名和密码，成功后调用公共会话服务签发双 Token。
 */
@Service
@RequiredArgsConstructor
public class SysUserAuthServiceImpl implements SysUserAuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;
    private final PasswordVerificationGuard passwordVerificationGuard;

    /**
     * 系统用户登录。
     *
     * @param loginDTO 登录参数
     * @return 双 Token
     */
    @Override
    public TokenPairVO login(SysUserLoginDTO loginDTO) {
        SysUser sysUser = sysUserMapper.selectByUsername(loginDTO.username());

        // 账号不存在和密码错误使用同一提示，避免暴露账号是否存在。
        if (sysUser == null || !passwordVerificationGuard.matches(
                passwordEncoder, loginDTO.password(), sysUser.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 先验证凭据，再向账号持有人返回停用状态。
        if (!Integer.valueOf(1).equals(sysUser.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_USER_DISABLED);
        }

        return authSessionService.createSession(AccountType.SYS_USER, sysUser.getId(), sysUser.getAuthVersion());
    }

    /**
     * 使用 Refresh Token 换取新的 Access Token。
     */
    @Override
    public AccessTokenVO refresh(RefreshTokenDTO refreshTokenDTO) {
        return authSessionService.refresh(refreshTokenDTO.refreshToken(), AccountType.SYS_USER);
    }

    /**
     * 删除系统用户当前会话。
     */
    @Override
    public void logout(String sessionId) {
        authSessionService.logout(AccountType.SYS_USER, sessionId);
    }

    /**
     * 获取当前登录系统用户信息。
     */
    @Override
    public SysUserInfoVO getCurrentSysUser(Long sysUserId) {
        SysUser sysUser = sysUserMapper.selectById(sysUserId);
        // Access Token 有效期内账号可能已被删除或停用，资料查询以数据库最新状态为准。
        if (sysUser == null) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        if (!Integer.valueOf(1).equals(sysUser.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_USER_DISABLED);
        }

        return new SysUserInfoVO(
                sysUser.getId(),
                sysUser.getUsername(),
                sysUser.getNickname()
        );
    }
}
