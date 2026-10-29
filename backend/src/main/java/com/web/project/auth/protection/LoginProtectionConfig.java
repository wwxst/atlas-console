package com.web.project.auth.protection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 登录保护配置注册。 */
@Configuration
@EnableConfigurationProperties(LoginProtectionProperties.class)
public class LoginProtectionConfig {
}
