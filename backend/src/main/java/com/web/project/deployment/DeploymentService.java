package com.web.project.deployment;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Proxies a narrow, authenticated API to the per-install updater agent.
 * GitHub Releases are public: customer servers never need a GitHub PAT or Runner.
 */
@Slf4j
@Service
public class DeploymentService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String RELEASE_ID_PATTERN = "[0-9a-f]{40}_[0-9a-f]{40}";

    private final DeploymentProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

    @Autowired
    public DeploymentService(DeploymentProperties properties, ObjectMapper mapper) {
        this(properties, mapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
    }

    DeploymentService(DeploymentProperties properties, ObjectMapper mapper, HttpClient client) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = client;
    }

    public DeploymentStatus status() {
        if (!properties.configured()) {
            return DeploymentStatus.notConfigured();
        }
        return parseStatus(send(request("/api/status").GET().build(), 200));
    }

    public DeploymentStatus deploy() {
        requireConfigured();
        return parseStatus(send(request("/api/deploy")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json")
                .build(), 202));
    }

    public DeploymentStatus recover() {
        requireConfigured();
        return parseStatus(send(request("/api/recover")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json")
                .build(), 200));
    }

    public DeploymentStatus rollback() {
        requireConfigured();
        if (!rollbackAvailable()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有可回滚的上一版本");
        }
        return parseStatus(send(request("/api/rollback")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).header("Content-Type", "application/json")
                .build(), 202));
    }

    public List<ReleaseHistoryItem> history() {
        if (!properties.configured()) {
            return List.of();
        }
        Path directory = Path.of(properties.stateDirectory()).resolve("history");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        String currentId = manifestId(Path.of(properties.stateDirectory()).resolve("current.env"));
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> readRelease(path, currentId))
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(ReleaseHistoryItem::deployedAt).reversed())
                    .limit(50)
                    .toList();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    public DeploymentStatus rollbackHistorical(String releaseId) {
        requireConfigured();
        if (releaseId == null || !releaseId.matches(RELEASE_ID_PATTERN)
                || history().stream().noneMatch(item ->
                item.releaseId().equals(releaseId) && !item.current())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该历史版本不可回滚");
        }
        String body = "{\"releaseId\":\"" + releaseId + "\"}";
        return parseStatus(send(request("/api/rollback-history")
                .POST(HttpRequest.BodyPublishers.ofString(body)).header("Content-Type", "application/json")
                .build(), 202));
    }

    private ReleaseHistoryItem readRelease(Path path, String currentId) {
        String filename = path.getFileName().toString();
        if (!filename.matches(RELEASE_ID_PATTERN + "\\.env")) {
            return null;
        }
        String releaseId = filename.substring(0, filename.length() - 4);
        if (!releaseId.equals(manifestId(path))) {
            return null;
        }
        String[] revisions = releaseId.split("_", 2);
        try {
            String version = readVersionMetadata(Path.of(properties.stateDirectory())
                    .resolve("versions").resolve(releaseId + ".json"));
            return new ReleaseHistoryItem(releaseId, version, revisions[0], revisions[1],
                    Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant(),
                    releaseId.equals(currentId));
        } catch (IOException exception) {
            return null;
        }
    }

    private String readVersionMetadata(Path path) {
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return "未知版本";
            }
            JsonNode metadata = mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
            String version = metadata.path("version").asText("");
            return version.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
                    ? "v" + version : "未知版本";
        } catch (IOException | RuntimeException exception) {
            return "未知版本";
        }
    }

    private String manifestId(Path path) {
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.size() != 2
                    || !lines.get(0).matches("frontend=git-[0-9a-f]{40}")
                    || !lines.get(1).matches("backend=git-[0-9a-f]{40}")) {
                return null;
            }
            return lines.get(0).substring("frontend=git-".length()) + "_"
                    + lines.get(1).substring("backend=git-".length());
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private boolean rollbackAvailable() {
        try {
            Path directory = Path.of(properties.stateDirectory());
            return manifestId(directory.resolve("current.env")) != null
                    && manifestId(directory.resolve("previous.env")) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private DeploymentStatus parseStatus(String body) {
        try {
            JsonNode json = mapper.readTree(body);
            return new DeploymentStatus(
                    properties.configured(),
                    json.path("state").asText("unknown"),
                    nullableText(json, "conclusion"),
                    nullableText(json, "latestReleaseUrl"),
                    nullableText(json, "title"),
                    nullableText(json, "requestId"),
                    rollbackAvailable(),
                    null,
                    false,
                    nullableText(json, "currentVersion"),
                    nullableText(json, "latestVersion"),
                    nullableText(json, "latestReleaseUrl"),
                    nullableText(json, "releaseNotes"),
                    nullableText(json, "publishedAt"),
                    json.path("updateAvailable").asBoolean(false),
                    nullableText(json, "message"),
                    nullableText(json, "releaseCheckError"),
                    nullableText(json, "deploymentMode"));
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private String nullableText(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private String send(HttpRequest request, int expectedStatus) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409 || response.statusCode() == 400
                    || response.statusCode() == 404) {
                String message = "更新操作未被接受";
                try {
                    message = nullableText(mapper.readTree(response.body()), "message");
                    if (message == null) message = "更新操作未被接受";
                } catch (RuntimeException ignored) {
                    // Keep a safe generic error; never echo raw upstream content.
                }
                throw new BusinessException(ErrorCode.BAD_REQUEST, message);
            }
            if (response.statusCode() != expectedStatus) {
                throw unavailable();
            }
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    private HttpRequest.Builder request(String path) {
        URI base = URI.create(properties.agentUrl().replaceAll("/+$", ""));
        return HttpRequest.newBuilder(base.resolve(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + properties.agentToken())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "atlas-console-backend");
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "服务器更新执行器尚未初始化");
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                "服务器更新执行器暂不可用，请稍后再试");
    }
}
