package com.web.project.deployment;

/** A specific requested GitHub Actions production run, including recoverable stale states. */
public record DeploymentStatus(
        boolean configured,
        String state,
        String conclusion,
        String runUrl,
        String title,
        String requestId,
        boolean rollbackAvailable,
        Long runId,
        boolean recoveryAvailable
) {
    public DeploymentStatus(boolean configured, String state, String conclusion,
                            String runUrl, String title, String requestId) {
        this(configured, state, conclusion, runUrl, title, requestId, false, null, false);
    }

    public DeploymentStatus(boolean configured, String state, String conclusion,
                            String runUrl, String title, String requestId, boolean rollbackAvailable) {
        this(configured, state, conclusion, runUrl, title, requestId, rollbackAvailable, null, false);
    }
}
