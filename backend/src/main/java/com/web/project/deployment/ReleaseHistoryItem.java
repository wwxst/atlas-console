package com.web.project.deployment;

import java.time.Instant;

/** A previously deployed application release recorded on this customer server. */
public record ReleaseHistoryItem(
        String releaseId,
        String version,
        String frontendSha,
        String backendSha,
        Instant deployedAt,
        boolean current
) {
}
