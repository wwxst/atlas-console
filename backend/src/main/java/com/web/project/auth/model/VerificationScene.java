package com.web.project.auth.model;

import java.util.Locale;

/**
 * 验证码场景。
 *
 * 不同场景对目标联系方式有不同的资格要求。
 */
public enum VerificationScene {

    /**
     * 注册：目标尚未注册。
     */
    REGISTER,

    /**
     * 验证码登录：目标已经注册。
     */
    LOGIN,

    /**
     * 重置密码：目标已经注册。
     */
    RESET_PASSWORD,

    /**
     * 换绑旧联系方式：目标属于当前用户。
     */
    REBIND_OLD,

    /**
     * 换绑新联系方式：目标未被其他用户绑定。
     */
    REBIND_NEW;

    /**
     * @return Redis key 中使用的场景段
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
