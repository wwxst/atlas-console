package com.web.project.deployment;

import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentServiceTest {
    private final HttpClient client = mock(HttpClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void updaterIsDisabledByDefaultAndNeverCallsGithubOrRedis() {
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(false, null, "/var/lib/atlas/releases"), mapper, redis, client);
        DeploymentStatus status = service.status();
        assertFalse(status.configured());
        assertEquals("not_configured", status.state());
        assertThrows(BusinessException.class, service::deploy);
        verifyNoInteractions(client, redis);
    }

    @Test
    void anEmptyTokenCannotEnableUpdater() {
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, " ", "/var/lib/atlas/releases"), mapper, redis, client);
        assertFalse(service.status().configured());
        assertThrows(BusinessException.class, service::deploy);
        verifyNoInteractions(client, redis);
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusReadsOnlyFixedProductionWorkflowAndSanitizesResponse() throws Exception {
        HttpResponse<String> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn("{\"workflow_runs\":[{\"status\":\"completed\","
                + "\"conclusion\":\"success\",\"display_title\":\"Atlas Deploy abc\","
                + "\"html_url\":\"https://github.com/wwxst/atlas-console/actions/runs/42\"}]}");
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(reply);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-gh-token", "/var/lib/atlas/releases"), mapper, redis, client);

        DeploymentStatus status = service.status();

        assertTrue(status.configured());
        assertEquals("completed", status.state());
        assertEquals("success", status.conclusion());
        org.mockito.ArgumentCaptor<HttpRequest> request =
                org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("api.github.com", request.getValue().uri().getHost());
        assertTrue(request.getValue().uri().getPath().endsWith("/deploy-production.yml/runs"));
        assertEquals("Bearer test-gh-token",
                request.getValue().headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchUsesMainAndRejectsImmediateDuplicate() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("atlas:deploy:dispatch-lock", "1", Duration.ofSeconds(90)))
                .thenReturn(true, false);
        HttpResponse<String> latest = mock(HttpResponse.class);
        when(latest.statusCode()).thenReturn(200);
        when(latest.body()).thenReturn("{\"workflow_runs\":[]}");
        HttpResponse<String> created = mock(HttpResponse.class);
        when(created.statusCode()).thenReturn(204);
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(latest, created);
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-gh-token", "/var/lib/atlas/releases"), mapper, redis, client);

        DeploymentStatus started = service.deploy();
        assertEquals("requested", started.state());
        assertNotNull(started.requestId());
        assertThrows(BusinessException.class, service::deploy);
        verify(client, times(2)).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void rollbackRequiresPreviousReleaseAndIgnoresMalformedManifest(@TempDir Path state) throws Exception {
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-token", state.toString()), mapper, redis, client);
        assertThrows(BusinessException.class, service::rollback);
        verifyNoInteractions(redis, client);

        String valid = "frontend=git-" + "a".repeat(40) + "\n"
                + "backend=git-" + "b".repeat(40) + "\n";
        Files.writeString(state.resolve("current.env"), valid);
        Files.writeString(state.resolve("previous.env"), "invalid content");
        assertThrows(BusinessException.class, service::rollback);
        verifyNoInteractions(redis, client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rollbackUsesFixedWorkflowWhenValidPreviousReleaseExists(@TempDir Path state) throws Exception {
        String previous = "frontend=git-" + "a".repeat(40) + "\n"
                + "backend=git-" + "b".repeat(40) + "\n";
        Files.writeString(state.resolve("current.env"), previous);
        Files.writeString(state.resolve("previous.env"), previous);

        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("atlas:deploy:dispatch-lock", "1", Duration.ofSeconds(90)))
                .thenReturn(true);
        HttpResponse<String> latest = mock(HttpResponse.class);
        when(latest.statusCode()).thenReturn(200);
        when(latest.body()).thenReturn("{\"workflow_runs\":[]}");
        HttpResponse<String> accepted = mock(HttpResponse.class);
        when(accepted.statusCode()).thenReturn(204);
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(latest, accepted);

        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-token", state.toString()), mapper, redis, client);
        assertTrue(service.status().rollbackAvailable());
        reset(client);
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(latest, accepted);
        assertEquals("requested", service.rollback().state());
        verify(client, times(2)).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void historyListsOnlyValidatedManifestsAndMarksCurrent(@TempDir Path state) throws Exception {
        Path archive = Files.createDirectory(state.resolve("history"));
        String a = "a".repeat(40), b = "b".repeat(40), c = "c".repeat(40), d = "d".repeat(40);
        Files.writeString(archive.resolve(a + "_" + b + ".env"),
                "frontend=git-" + a + "\nbackend=git-" + b + "\n");
        Files.writeString(archive.resolve(c + "_" + d + ".env"),
                "frontend=git-" + c + "\nbackend=git-" + d + "\n");
        Files.writeString(state.resolve("current.env"),
                "frontend=git-" + c + "\nbackend=git-" + d + "\n");
        Files.writeString(archive.resolve("malicious.env"), "invalid");
        Files.writeString(archive.resolve(a + "_" + d + ".env"), "frontend=git-" + c + "\nbackend=git-" + d + "\n");

        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-token", state.toString()), mapper, redis, client);
        var items = service.history();
        assertEquals(2, items.size());
        assertTrue(items.stream().anyMatch(item -> item.releaseId().equals(c + "_" + d) && item.current()));
        assertTrue(items.stream().anyMatch(item -> item.releaseId().equals(a + "_" + b) && !item.current()));
        assertNotNull(items.getFirst().deployedAt());
        assertThrows(BusinessException.class, () -> service.rollbackHistorical(c + "_" + d));
        assertThrows(BusinessException.class, () -> service.rollbackHistorical("../current.env"));
        assertThrows(BusinessException.class, () -> service.rollbackHistorical("f".repeat(40) + "_" + "e".repeat(40)));
        verifyNoInteractions(redis, client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rollbackHistoricalDispatchesWhitelistedReleaseOnly(@TempDir Path state) throws Exception {
        Path archive = Files.createDirectory(state.resolve("history"));
        String a = "a".repeat(40), b = "b".repeat(40), c = "c".repeat(40), d = "d".repeat(40);
        String id = a + "_" + b;
        Files.writeString(archive.resolve(id + ".env"),
                "frontend=git-" + a + "\nbackend=git-" + b + "\n");
        Files.writeString(state.resolve("current.env"),
                "frontend=git-" + c + "\nbackend=git-" + d + "\n");

        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("atlas:deploy:dispatch-lock", "1", Duration.ofSeconds(90))).thenReturn(true);
        HttpResponse<String> latest = mock(HttpResponse.class);
        when(latest.statusCode()).thenReturn(200);
        when(latest.body()).thenReturn("{\"workflow_runs\":[]}");
        HttpResponse<String> accepted = mock(HttpResponse.class);
        when(accepted.statusCode()).thenReturn(204);
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(latest, accepted);

        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-token", state.toString()), mapper, redis, client);
        assertEquals("requested", service.rollbackHistorical(id).state());
        var req = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(req.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("POST", req.getAllValues().getLast().method());
        assertTrue(req.getAllValues().getLast().uri().getPath().endsWith("/dispatches"));
    }

    @Test
    void propertyStringNeverExposesGitHubToken() {
        String info = new DeploymentProperties(true, "sensitive-test-credential", "/var/lib/atlas/releases").toString();
        assertTrue(info.contains("redacted"));
        assertFalse(info.contains("sensitive-test-credential"));
    }
}
