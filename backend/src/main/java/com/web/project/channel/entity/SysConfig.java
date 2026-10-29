package com.web.project.channel.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统配置实体。
 *
 * 对应数据库中的 sys_config 表，当前用于保存渠道开关。
 */
@Getter
@Setter
public class SysConfig {

    private String configKey;

    private String configValue;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
