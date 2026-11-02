package com.web.project.message.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 已在短信服务商控制台登记的模板资料。 */
@Getter
@Setter
public class SmsTemplate {
    private Long id;
    private String provider;
    private String name;
    private String templateCode;
    private String kind;
    private String content;
    private String variablesJson;
    private String reviewStatus;
    private boolean enabled;
    private LocalDateTime updatedAt;
}
