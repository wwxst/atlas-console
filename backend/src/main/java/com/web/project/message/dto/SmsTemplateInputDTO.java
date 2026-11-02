package com.web.project.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 新增或修改短信模板资料。 */
public record SmsTemplateInputDTO(
        @NotBlank(message = "请选择短信平台")
        @Pattern(regexp = "aliyun|tencent", message = "短信平台不正确")
        String provider,
        @NotBlank(message = "请输入模板名称") @Size(max = 80)
        String name,
        @NotBlank(message = "请输入服务商模板 ID") @Size(max = 100)
        String templateCode,
        @NotBlank(message = "请选择模板类型")
        @Pattern(regexp = "verification|notification|marketing", message = "模板类型不正确")
        String kind,
        @NotBlank(message = "请输入模板内容") @Size(max = 1000)
        String content,
        @NotNull(message = "模板变量不能为空") @Size(max = 20)
        List<@NotBlank @Size(max = 50) String> variables,
        @NotBlank(message = "请选择审核状态")
        @Pattern(regexp = "pending|approved|rejected", message = "审核状态不正确")
        String reviewStatus,
        @NotNull(message = "请选择模板是否启用")
        Boolean enabled
) {
}
