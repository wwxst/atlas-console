package com.web.project.channel.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** SMTP 邮箱验证码配置；password 为空时保留已保存的授权码。 */
public record EmailLoginSettingsUpdateDTO(
        @NotBlank(message = "请输入 SMTP 服务器")
        @Size(max = 255)
        @Pattern(regexp = "[A-Za-z0-9.-]+", message = "SMTP 服务器格式不正确")
        String host,
        @NotNull(message = "请输入 SMTP 端口") @Min(1) @Max(65535)
        Integer port,
        @NotBlank(message = "请输入 SMTP 用户名") @Size(max = 254)
        String username,
        @NotNull(message = "SMTP 密码或授权码不能为空") @Size(max = 128)
        String password,
        @NotBlank(message = "请输入发件邮箱") @Email @Size(max = 254)
        String fromAddress,
        @NotBlank(message = "请输入发件人名称") @Size(max = 80)
        String fromName,
        @NotBlank(message = "请选择 SMTP 加密方式")
        @Pattern(regexp = "SSL|STARTTLS|NONE", message = "SMTP 加密方式不正确")
        String security
) {
    @Override
    public String toString() {
        return "EmailLoginSettingsUpdateDTO[host=redacted, port=" + port
                + ", username=redacted, password=redacted, fromAddress=redacted, fromName=redacted, security="
                + security + "]";
    }
}
