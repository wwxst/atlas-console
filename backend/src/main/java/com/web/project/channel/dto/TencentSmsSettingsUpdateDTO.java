package com.web.project.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 腾讯云短信登录配置；SecretKey 为空时保留已保存的密钥。 */
public record TencentSmsSettingsUpdateDTO(
        @NotBlank(message = "请输入 SecretId") @Size(max = 128)
        String secretId,
        @NotNull(message = "SecretKey 不能为空") @Size(max = 128)
        String secretKey,
        @NotBlank(message = "请输入短信 SDK App ID") @Size(max = 32)
        String sdkAppId,
        @NotBlank(message = "请输入短信签名") @Size(max = 50)
        String signName,
        @NotBlank(message = "请输入登录验证码模板 ID") @Size(max = 100)
        String templateId,
        @NotBlank(message = "请选择接入地域")
        @Pattern(regexp = "ap-guangzhou|ap-shanghai|ap-beijing|ap-chengdu|ap-hongkong|ap-singapore",
                message = "腾讯云短信地域不受支持")
        String region
) {
    @Override
    public String toString() {
        return "TencentSmsSettingsUpdateDTO[secretId=redacted, secretKey=redacted, sdkAppId=redacted, signName=redacted, templateId=redacted, region=redacted]";
    }
}
