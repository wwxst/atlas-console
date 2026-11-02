package com.web.project.deployment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

/**
 * Per-install credentials for the private local updater agent.
 * No GitHub PAT, Actions runner, or customer GitHub account is required.
 */
@ConfigurationProperties(prefix = "app.deployment")
public record DeploymentProperties(
        boolean enabled,
        String agentUrl,
        String agentToken,
        String stateDirectory
) {
    public boolean configured() {
        if (!enabled || agentToken == null || agentToken.length() < 48
                || agentUrl == null || agentUrl.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(agentUrl);
            return "http".equals(uri.getScheme()) && uri.getHost() != null
                    && uri.getPort() > 0 && uri.getUserInfo() == null
                    && uri.getQuery() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public String toString() {
        return "DeploymentProperties[enabled=" + enabled + ", agentUrl=" + agentUrl
                + ", agentToken=redacted, stateDirectory=" + stateDirectory + "]";
    }
}
