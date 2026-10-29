package com.web.project.auth.vo;
/**
 * 当前登录系统用户信息。
 *
 * @param id       系统用户 ID
 * @param username 系统用户账号
 * @param nickname 系统用户昵称
 */
public record SysUserInfoVO(
        Long id,
        String username,
        String nickname
) {
}
