package com.web.project.deployment;

import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentTrackingTest {
    private static final String TRACKED = "atlas:deploy:tracked-request";
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final HttpClient http = mock(HttpClient.class);
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> saved = new HashMap<>();
    private final Clock now = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);

    private DeploymentService service(Clock clock) {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> saved.get(call.getArgument(0)));
        doAnswer(call -> { saved.put(call.getArgument(0), call.getArgument(1)); return null; })
                .when(values).set(eq(TRACKED), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(call -> saved.remove(call.getArgument(0)) != null);
        return new DeploymentService(
                new DeploymentProperties(true, "test-token", "/missing/deployment/manifests"),
                json, redis, http, clock);
    }

    @SuppressWarnings("unchecked")
    private void replies(String runsJson, String matchedRunJson) throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            String path = request.uri().getPath();
            HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(path.endsWith("/runs/42") ? matchedRunJson : runsJson);
            return response;
        });
    }

    private static String run(String title, String status, String conclusion) {
        return "{\"id\":42,\"display_title\":\"" + title
                + "\",\"status\":\"" + status
                + "\",\"conclusion\":" + (conclusion == null ? "null" : "\"" + conclusion + "\"")
                + ",\"html_url\":\"https://github.com/wwxst/atlas-console/actions/runs/42\"}";
    }

    @Test
    void correlatedRunIsFoundEvenWhenNewerUnrelatedRunIsFirstThenPinnedById() throws Exception {
        DeploymentService service = service(now);
        String id = "12345678-1234-1234-1234-123456789abc";
        saved.put(TRACKED, json.writeValueAsString(
                new DeploymentService.TrackedRun(id, "deploy", now.millis(), null)));
        String title = "Atlas deploy " + id;
        String matched = run(title, "in_progress", null);
        replies("{\"workflow_runs\":[" + run("Atlas deploy unrelated", "completed", "success")
                + "," + matched + "]}", matched);

        DeploymentStatus first = service.status();
        assertEquals(id, first.requestId());
        assertEquals(42L, first.runId());
        assertEquals("in_progress", first.state());
        assertTrue(saved.get(TRACKED).contains("\"runId\":42"));

        DeploymentStatus second = service.status();
        assertEquals(42L, second.runId());
        var requests = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(requests.getAllValues().getLast().uri().getPath().endsWith("/actions/runs/42"));
    }

    @Test
    void noRunCreatedTimesOutAndCanBeRecoveredWithoutReload() throws Exception {
        DeploymentService service = service(now);
        String id = "12345678-1234-1234-1234-123456789abc";
        saved.put(TRACKED, json.writeValueAsString(new DeploymentService.TrackedRun(
                id, "deploy", now.millis() - Duration.ofMinutes(6).toMillis(), null)));
        replies("{\"workflow_runs\":[]}", "{}");
        when(values.setIfAbsent(eq("atlas:deploy:dispatch-lock"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        DeploymentStatus stale = service.status();
        assertEquals("timed_out", stale.state());
        assertTrue(stale.recoveryAvailable());
        assertEquals(id, stale.requestId());

        DeploymentStatus recovered = service.recover();
        assertEquals("not_started", recovered.state());
        assertNull(recovered.requestId());
        assertFalse(saved.containsKey(TRACKED));
    }

    @Test
    void staleQueuedRunCannotBeClearedWhileGithubStillHasAnActiveRun() throws Exception {
        DeploymentService service = service(now);
        String id = "12345678-1234-1234-1234-123456789abc";
        saved.put(TRACKED, json.writeValueAsString(new DeploymentService.TrackedRun(
                id, "rollback", now.millis() - Duration.ofHours(2).toMillis(), 42L)));
        String queued = run("Atlas rollback " + id, "queued", null);
        replies("{\"workflow_runs\":[" + queued + "]}", queued);
        when(values.setIfAbsent(eq("atlas:deploy:dispatch-lock"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        assertEquals("stalled", service.status().state());
        assertThrows(BusinessException.class, service::recover);
        assertTrue(saved.containsKey(TRACKED));
    }

    @Test
    void trackedRunCompletionIsTerminalAndDoesNotDependOnLaterRuns() throws Exception {
        DeploymentService service = service(now);
        String id = "12345678-1234-1234-1234-123456789abc";
        saved.put(TRACKED, json.writeValueAsString(new DeploymentService.TrackedRun(
                id, "rollback-history", now.millis() - Duration.ofMinutes(3).toMillis(), 42L)));
        String completed = run("Atlas rollback-history " + id, "completed", "failure");
        replies("{\"workflow_runs\":[]}", completed);

        DeploymentStatus outcome = service.status();
        assertEquals("completed", outcome.state());
        assertEquals("failure", outcome.conclusion());
        assertFalse(outcome.recoveryAvailable());
        assertEquals(id, outcome.requestId());
    }

    @Test
    void malformedTrackedRequestFailsClosedInsteadOfCheckingUnrelatedLatestRun() throws Exception {
        DeploymentService service = service(now);
        saved.put(TRACKED, "{\"requestId\":\"../../invalid\"}");
        replies("{\"workflow_runs\":[]}", "{}");
        assertThrows(BusinessException.class, service::status);
        verifyNoInteractions(http);
    }
}
