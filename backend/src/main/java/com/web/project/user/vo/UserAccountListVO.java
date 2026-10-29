package com.web.project.user.vo;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 后台用户列表返回数据。
 *
 * 不返回 password 和 password_version 字段。
 *
 * @param id        用户 ID
 * @param userNo    对外展示号码
 * @param phone     手机号
 * @param email     邮箱
 * @param nickname  用户昵称
 * @param avatar    头像地址
 * @param status    状态：0禁用，1正常
 * @param online    最近三分钟是否有已认证的 API 活动
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record UserAccountListVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String userNo,
        String phone,
        String email,
        String nickname,
        String avatar,
        Integer status,
        boolean online,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public UserAccountListVO(Long id, String userNo, String phone, String email, String nickname,
                             String avatar, Integer status, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, userNo, phone, email, nickname, avatar, status, false, createdAt, updatedAt);
    }

    @Override
    public String toString() {
        return "UserAccountListVO[id=" + id + ", userNo=" + userNo + ", contacts=redacted]";
    }
}
