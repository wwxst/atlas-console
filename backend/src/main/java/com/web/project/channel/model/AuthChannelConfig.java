package com.web.project.channel.model;

import com.web.project.auth.model.TargetType;

/**
 * 渠道配置。
 *
 * 对应 sys_config 中 4 个布尔配置：手机号总开关、手机验证码开关、
 * 邮箱总开关、邮箱验证码开关。
 *
 * @param phoneEnabled     手机号注册、登录和绑定是否可用
 * @param phoneCodeEnabled 手机验证码是否可用
 * @param emailEnabled     邮箱注册、登录和绑定是否可用
 * @param emailCodeEnabled 邮箱验证码是否可用
 */
public record AuthChannelConfig(
        boolean phoneEnabled,
        boolean phoneCodeEnabled,
        boolean emailEnabled,
        boolean emailCodeEnabled
) {

    /**
     * 指定渠道的总开关是否开启。
     */
    public boolean enabled(TargetType targetType) {
        return targetType == TargetType.PHONE ? phoneEnabled : emailEnabled;
    }

    /**
     * 指定渠道的验证码开关是否开启。
     */
    public boolean codeEnabled(TargetType targetType) {
        return targetType == TargetType.PHONE ? phoneCodeEnabled : emailCodeEnabled;
    }
}
