package com.web.project.channel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 登录方式与默认短信服务商配置。 */
public record LoginSettingsUpdateDTO(
        @NotNull(message = "请选择是否启用手机登录")
        Boolean phoneLoginEnabled,
        @NotNull(message = "请选择是否启用邮箱登录")
        Boolean emailLoginEnabled,
        @NotNull(message = "请选择短信服务商")
        @Pattern(regexp = "aliyun|tencent", message = "短信服务商只能是 aliyun 或 tencent")
        String smsProvider
) {
}
