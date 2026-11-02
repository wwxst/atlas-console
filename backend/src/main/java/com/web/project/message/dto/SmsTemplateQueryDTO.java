package com.web.project.message.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 短信模板分页筛选条件。 */
public record SmsTemplateQueryDTO(
        @Min(value = 1, message = "页码至少为 1") @Max(value = 1000000, message = "页码超出范围")
        Integer page,
        @Min(value = 1, message = "每页数量至少为 1") @Max(value = 100, message = "每页最多 100 条")
        Integer pageSize,
        @Size(max = 100, message = "搜索内容不能超过 100 个字符")
        String keyword,
        @Pattern(regexp = "aliyun|tencent", message = "短信平台不正确")
        String provider,
        @Pattern(regexp = "verification|notification|marketing", message = "模板类型不正确")
        String kind,
        @Pattern(regexp = "pending|approved|rejected", message = "审核状态不正确")
        String reviewStatus
) {
    public int normalizedPage() { return page == null ? 1 : page; }
    public int normalizedPageSize() { return pageSize == null ? 20 : pageSize; }
}
