package com.web.project.user.vo;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 当前普通用户信息。
 *
 * 不返回密码和密码版本。
 *
 * @param id        用户ID
 * @param userNo    对外展示号码
 * @param phone     手机号
 * @param email     邮箱
 * @param nickname  用户昵称
 * @param avatar    头像地址
 * @param status    用户状态
 * @param createdAt 创建时间
 */
public record UserInfoVO(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String userNo,
        String phone,
        String email,
        String nickname,
        String avatar,
        Integer status,
        LocalDateTime createdAt
) {
    @Override
    public String toString() {
        return "UserInfoVO[id=" + id + ", userNo=" + userNo + ", contacts=redacted]";
    }
}
