package com.web.project.message.vo;

import java.time.LocalDateTime;
import java.util.Map;

/** 通知场景配置响应。 */
public record MessageNotificationVO(
        Long id,
        String category,
        String code,
        String name,
        String description,
        Channels channels,
        String siteMessageTitle,
        String siteMessageContent,
        Long smsTemplateId,
        Map<String, String> smsVariableMappings,
        LocalDateTime updatedAt
) {
    public record Channels(boolean siteMessage, boolean sms) {
    }
}
