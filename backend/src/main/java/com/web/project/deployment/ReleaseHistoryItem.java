package com.web.project.deployment;

import java.time.Instant;

/**
 * A previously deployed frontend/backend image pair recorded by the runner.
 * A history entry is metadata, not proof that its Docker images still exist.
 */
public record ReleaseHistoryItem(
        String releaseId,
        String frontendSha,
        String backendSha,
        Instant deployedAt,
        boolean current
) {
}
