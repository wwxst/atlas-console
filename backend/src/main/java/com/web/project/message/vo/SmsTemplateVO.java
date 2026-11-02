package com.web.project.message.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 短信模板列表与编辑响应。 */
public record SmsTemplateVO(
        Long id,
        String provider,
        String name,
        String templateCode,
        String kind,
        String content,
        List<String> variables,
        String reviewStatus,
        boolean enabled,
        LocalDateTime updatedAt
) {
}
