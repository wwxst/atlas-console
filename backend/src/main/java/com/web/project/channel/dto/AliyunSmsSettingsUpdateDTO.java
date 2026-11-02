package com.web.project.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 阿里云短信登录配置；AccessKey Secret 为空时保留已保存的密钥。 */
public record AliyunSmsSettingsUpdateDTO(
        @NotBlank(message = "请输入 AccessKey ID") @Size(max = 128)
        String accessKeyId,
        @NotNull(message = "AccessKey Secret 不能为空") @Size(max = 128)
        String accessKeySecret,
        @NotBlank(message = "请输入短信签名") @Size(max = 50)
        String signName,
        @NotBlank(message = "请输入登录验证码模板 Code") @Size(max = 100)
        String templateCode
) {
    @Override
    public String toString() {
        return "AliyunSmsSettingsUpdateDTO[accessKeyId=redacted, accessKeySecret=redacted, signName=redacted, templateCode=redacted]";
    }
}
