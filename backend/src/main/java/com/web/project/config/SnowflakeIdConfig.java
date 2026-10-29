package com.web.project.config;

import com.web.project.common.id.SnowflakeIdGenerator;
import com.web.project.config.properties.SnowflakeIdProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the application-wide Snowflake ID generator. */
@Configuration
@EnableConfigurationProperties(SnowflakeIdProperties.class)
public class SnowflakeIdConfig {

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(SnowflakeIdProperties properties) {
        return new SnowflakeIdGenerator(properties.datacenterId(), properties.workerId());
    }
}
