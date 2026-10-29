package com.web.project.deployment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeploymentProperties.class)
public class DeploymentConfig {
}
