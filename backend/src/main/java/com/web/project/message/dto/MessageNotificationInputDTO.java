package com.web.project.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** 新增或更新通知场景的渠道配置。 */
public record MessageNotificationInputDTO(
        @NotBlank
        @Pattern(regexp = "member|platform|custom", message = "通知分类不正确")
        String category,
        @NotBlank
        @Pattern(regexp = "[a-z][a-z0-9_.-]{0,79}", message = "通知编码格式不正确")
        String code,
        @NotBlank @Size(max = 80)
        String name,
        @NotBlank @Size(max = 200)
        String description,
        @NotNull @Valid
        MessageChannelsDTO channels,
        @Size(max = 120)
        String siteMessageTitle,
        @Size(max = 4000)
        String siteMessageContent,
        @Positive
        Long smsTemplateId,
        @NotNull @Size(max = 20)
        Map<
                @NotBlank
                @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_]{0,49}", message = "短信模板变量名称不正确")
                String,
                @NotBlank
                @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.]{0,79}", message = "业务变量映射格式不正确")
                String
        > smsVariableMappings
) {
    public record MessageChannelsDTO(
            @NotNull Boolean siteMessage,
            @NotNull Boolean sms
    ) {
    }
}
