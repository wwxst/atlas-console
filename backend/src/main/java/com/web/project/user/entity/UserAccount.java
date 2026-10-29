package com.web.project.user.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 普通用户账号实体。
 *
 * 对应数据库中的 user_account 表。
 */
@Getter
@Setter
public class UserAccount {

    /**
     * 用户主键 ID。
     */
    private Long id;

    /**
     * 对外展示号码，不参与登录和鉴权。
     */
    private String userNo;

    /**
     * 11 位中国大陆手机号；未绑定时为 null。
     */
    private String phone;

    /**
     * 规范化为小写的邮箱；未绑定时为 null。
     */
    private String email;

    /**
     * BCrypt 加密后的密码；验证码注册的账号可以为空。
     */
    private String password;

    /**
     * 密码版本，修改或重置密码时递增。
     */
    private Long passwordVersion;

    private String nickname;

    private String avatar;

    /**
     * 用户状态：
     * 0 表示禁用，1 表示正常。
     */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
