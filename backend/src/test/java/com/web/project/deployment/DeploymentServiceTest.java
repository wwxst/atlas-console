package com.web.project.deployment;

import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeploymentServiceTest {
    private final HttpClient client = mock(HttpClient.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private DeploymentProperties properties(Path state) {
        return new DeploymentProperties(true, "http://updater:8090", "x".repeat(64), state.toString());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Test
    void disabledUpdaterDoesNotCallAgentOrExposeAnUpdateButton() {
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(false, "http://updater:8090", "x".repeat(64), "/var/lib/atlas/releases"),
                mapper, client);
        DeploymentStatus status = service.status();
        assertFalse(status.configured());
        assertEquals("not_configured", status.state());
        assertFalse(status.updateAvailable());
        assertThrows(BusinessException.class, service::deploy);
        verifyNoInteractions(client);
    }

    @Test
    void invalidOrMissingPerInstallTokenCannotEnableUpdater() {
        for (DeploymentProperties properties : new DeploymentProperties[]{
                new DeploymentProperties(true, "http://updater:8090", "short", "/tmp/releases"),
                new DeploymentProperties(true, "https://updater:8090", "x".repeat(64), "/tmp/releases"),
                new DeploymentProperties(true, "http://updater", "x".repeat(64), "/tmp/releases")
        }) {
            assertFalse(properties.configured());
        }
    }

    @Test
    void statusReadsPublicReleaseMetadataFromPrivateAgentAndNeverCallsGithubDirectly() throws Exception {
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response(200, """
                        {"state":"idle","conclusion":null,"currentVersion":"0.1.0",
                         "latestVersion":"0.1.1","latestReleaseUrl":"https://github.com/wwxst/atlas-console/releases/tag/v0.1.1",
                         "releaseNotes":"Fixes","updateAvailable":true}
                        """));
        DeploymentService service = new DeploymentService(properties(Path.of("/tmp/atlas-release-test")),
                mapper, client);

        DeploymentStatus status = service.status();

        assertTrue(status.configured());
        assertEquals("idle", status.state());
        assertEquals("0.1.0", status.currentVersion());
        assertEquals("0.1.1", status.latestVersion());
        assertTrue(status.updateAvailable());
        assertEquals("Fixes", status.releaseNotes());
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("updater", request.getValue().uri().getHost());
        assertEquals("/api/status", request.getValue().uri().getPath());
        assertEquals("Bearer " + "x".repeat(64),
                request.getValue().headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void deployDispatchesOnlyToTheLocalAgentAndReturnsItsAcceptedStatus(@TempDir Path state) throws Exception {
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response(202, """
                        {"state":"in_progress","requestId":"12345678-1234-1234-1234-123456789abc",
                         "currentVersion":"0.1.0","latestVersion":"0.1.1","updateAvailable":true}
                        """));
        DeploymentService service = new DeploymentService(properties(state), mapper, client);

        DeploymentStatus started = service.deploy();

        assertEquals("in_progress", started.state());
        assertEquals("12345678-1234-1234-1234-123456789abc", started.requestId());
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("POST", request.getValue().method());
        assertEquals("/api/deploy", request.getValue().uri().getPath());
    }

    @Test
    void agentConflictsAreReturnedAsSafeBusinessErrors() throws Exception {
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response(409, "{\"message\":\"当前已经是最新版本\"}"));
        DeploymentService service = new DeploymentService(properties(Path.of("/tmp/atlas-release-test")),
                mapper, client);
        BusinessException error = assertThrows(BusinessException.class, service::deploy);
        assertTrue(error.getMessage().contains("当前已经是最新版本"));
    }

    @Test
    void rollbackRequiresAValidPreviousManifestAndCallsOnlyLocalAgent(@TempDir Path state) throws Exception {
        DeploymentService service = new DeploymentService(properties(state), mapper, client);
        assertThrows(BusinessException.class, service::rollback);
        verifyNoInteractions(client);

        String front = "a".repeat(40), back = "b".repeat(40);
        Files.writeString(state.resolve("current.env"),
                "frontend=git-" + front + "\nbackend=git-" + back + "\n");
        Files.writeString(state.resolve("previous.env"),
                "frontend=git-" + front + "\nbackend=git-" + back + "\n");
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response(202, "{\"state\":\"in_progress\"}"));

        assertEquals("in_progress", service.rollback().state());
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("/api/rollback", request.getValue().uri().getPath());
    }

    @Test
    void historyShowsValidatedReleaseVersionsAndRejectsUntrustedPaths(@TempDir Path state) throws Exception {
        Path archive = Files.createDirectory(state.resolve("history"));
        Path versions = Files.createDirectory(state.resolve("versions"));
        String a = "a".repeat(40), b = "b".repeat(40), c = "c".repeat(40), d = "d".repeat(40);
        String current = c + "_" + d;
        Files.writeString(archive.resolve(a + "_" + b + ".env"),
                "frontend=git-" + a + "\nbackend=git-" + b + "\n");
        Files.writeString(archive.resolve(current + ".env"),
                "frontend=git-" + c + "\nbackend=git-" + d + "\n");
        Files.writeString(versions.resolve(a + "_" + b + ".json"), "{\"version\":\"0.1.0\"}");
        Files.writeString(versions.resolve(current + ".json"), "{\"version\":\"0.1.1\"}");
        Files.writeString(state.resolve("current.env"),
                "frontend=git-" + c + "\nbackend=git-" + d + "\n");
        Files.writeString(archive.resolve("malicious.env"), "invalid");

        DeploymentService service = new DeploymentService(properties(state), mapper, client);
        var items = service.history();
        assertEquals(2, items.size());
        assertTrue(items.stream().anyMatch(item -> item.releaseId().equals(current)
                && item.current() && item.version().equals("v0.1.1")));
        assertTrue(items.stream().anyMatch(item -> item.releaseId().equals(a + "_" + b)
                && !item.current() && item.version().equals("v0.1.0")));
        assertThrows(BusinessException.class, () -> service.rollbackHistorical("../current.env"));
        verifyNoInteractions(client);
    }

    @Test
    void historicalRollbackAcceptsOnlyAnExistingArchivedRelease(@TempDir Path state) throws Exception {
        Path archive = Files.createDirectory(state.resolve("history"));
        String a = "a".repeat(40), b = "b".repeat(40), c = "c".repeat(40), d = "d".repeat(40);
        String id = a + "_" + b;
        Files.writeString(archive.resolve(id + ".env"),
                "frontend=git-" + a + "\nbackend=git-" + b + "\n");
        Files.writeString(state.resolve("current.env"),
                "frontend=git-" + c + "\nbackend=git-" + d + "\n");
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response(202, "{\"state\":\"in_progress\"}"));

        DeploymentService service = new DeploymentService(properties(state), mapper, client);
        assertEquals("in_progress", service.rollbackHistorical(id).state());
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("/api/rollback-history", request.getValue().uri().getPath());
        assertTrue(request.getValue().bodyPublisher().isPresent());
    }

    @Test
    void propertyStringNeverExposesThePerInstallAgentToken() {
        String secret = "sensitive-test-agent-token";
        String info = new DeploymentProperties(true, "http://updater:8090", secret + "x".repeat(64),
                "/var/lib/atlas/releases").toString();
        assertTrue(info.contains("redacted"));
        assertFalse(info.contains(secret));
    }
}
