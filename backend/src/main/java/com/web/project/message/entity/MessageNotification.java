package com.web.project.message.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 业务通知场景配置；仅保存模板与渠道开关，不代表已触发业务发送。 */
@Getter
@Setter
public class MessageNotification {
    private Long id;
    private String category;
    private String code;
    private String name;
    private String description;
    private boolean siteMessageEnabled;
    private boolean smsEnabled;
    private String siteMessageTitle;
    private String siteMessageContent;
    private Long smsTemplateId;
    private String smsVariableMappingsJson;
    private LocalDateTime updatedAt;
}
