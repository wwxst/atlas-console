package com.web.project.deployment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment is disabled until an operator has provisioned a server, runner,
 * and a GitHub token scoped to the Atlas Console Actions workflow.
 */
@ConfigurationProperties(prefix = "app.deployment")
public record DeploymentProperties(boolean enabled, String githubToken, String stateDirectory) {

    public boolean configured() {
        return enabled && githubToken != null && !githubToken.isBlank();
    }

    @Override
    public String toString() {
        return "DeploymentProperties[enabled=" + enabled + ", githubToken=redacted]";
    }
}
