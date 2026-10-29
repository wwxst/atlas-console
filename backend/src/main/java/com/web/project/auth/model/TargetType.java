package com.web.project.auth.model;

import java.util.Locale;

/**
 * 联系方式类型。
 */
public enum TargetType {

    /**
     * 中国大陆手机号。
     */
    PHONE,

    /**
     * 邮箱。
     */
    EMAIL;

    /**
     * @return Redis key 中使用的类型段
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
