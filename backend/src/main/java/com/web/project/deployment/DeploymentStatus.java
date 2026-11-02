package com.web.project.deployment;

/** Public, secret-free status returned to the administrator update panel. */
public record DeploymentStatus(
        boolean configured,
        String state,
        String conclusion,
        String runUrl,
        String title,
        String requestId,
        boolean rollbackAvailable,
        Long runId,
        boolean recoveryAvailable,
        String currentVersion,
        String latestVersion,
        String latestReleaseUrl,
        String releaseNotes,
        String publishedAt,
        boolean updateAvailable,
        String message,
        String releaseCheckError
) {
    public static DeploymentStatus notConfigured() {
        return new DeploymentStatus(false, "not_configured", null, null, null, null,
                false, null, false, null, null, null, null, null, false,
                "更新服务尚未初始化", null);
    }
}
