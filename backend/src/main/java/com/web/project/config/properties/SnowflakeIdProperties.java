package com.web.project.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Snowflake ID node identity for this application instance. */
@ConfigurationProperties(prefix = "app.id-generator")
public record SnowflakeIdProperties(long datacenterId, long workerId) {

    @ConstructorBinding
    public SnowflakeIdProperties {
        if (datacenterId < 0 || datacenterId > 31) {
            throw new IllegalArgumentException("app.id-generator.datacenter-id 必须在 0 到 31 之间");
        }
        if (workerId < 0 || workerId > 31) {
            throw new IllegalArgumentException("app.id-generator.worker-id 必须在 0 到 31 之间");
        }
    }
}
