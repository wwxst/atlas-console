package com.web.project.admin.vo;

import java.time.LocalDateTime;

/**
 * 系统用户列表返回数据。
 *
 * 不包含 password，避免密码密文被返回给前端。
 *
 * @param id        系统用户 ID
 * @param username  登录账号
 * @param nickname  系统用户昵称
 * @param avatar    头像地址
 * @param status    状态：0禁用，1正常
 * @param online    最近三分钟是否有已认证的 API 活动
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record SysUserListVO(
        Long id,
        String username,
        String nickname,
        String avatar,
        Integer status,
        boolean online,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
