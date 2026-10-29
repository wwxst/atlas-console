package com.web.project.channel.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 渠道配置修改参数。
 *
 * @param enabled     该渠道的注册、登录和绑定能力是否启用
 * @param codeEnabled 该渠道的验证码能力是否启用
 */
public record AuthChannelUpdateDTO(

        @NotNull(message = "请选择是否启用该渠道")
        Boolean enabled,

        @NotNull(message = "请选择是否启用验证码")
        Boolean codeEnabled
) {
}
