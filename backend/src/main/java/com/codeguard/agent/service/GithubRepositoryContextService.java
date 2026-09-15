package com.codeguard.agent.service;

import com.codeguard.agent.domain.ChangedFile;
import com.codeguard.agent.domain.FileKind;
import com.codeguard.agent.domain.ParsedDiff;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewToolObservation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * GitHub PR 的只读上下文工具。它只接受已经保存到 Review 中的 GitHub PR URL，
 * 固定读取该 PR 的 head SHA，限制文件数量和内容长度，避免工具越界读取仓库内容。
 */
@Service
public class GithubRepositoryContextService {

    private static final Pattern PR_URL = Pattern.compile(
            "^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/pull/(\\d+)$"
    );
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9._/-]+");
    private static final int MAX_CONTENT_CHARS = 3_000;
    private static final String READ_CHANGED_FILE_TOOL = "github_read_changed_file";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String githubToken;

    public GithubRepositoryContextService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${codeguard.github.token:}") String githubToken
    ) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.githubToken = githubToken;
    }

    public List<ReviewToolObservation> collect(String sourceUrl, ParsedDiff parsedDiff) {
        Matcher matcher = sourceUrl == null ? null : PR_URL.matcher(sourceUrl);
        if (matcher == null || !matcher.matches()) {
            return List.of();
        }

        String repository = matcher.group(1) + "/" + matcher.group(2);
        int pullNumber = Integer.parseInt(matcher.group(3));
        try {
            JsonNode pull = getJson("https://api.github.com/repos/" + repository + "/pulls/" + pullNumber);
            String headSha = pull.path("head").path("sha").asText();
            if (!headSha.matches("[A-Fa-f0-9]{7,64}")) {
                return List.of(new ReviewToolObservation(
                        "github-pr-snapshot",
                        "GitHub PR 上下文不可用",
                        "GitHub 返回的 head SHA 无效，已跳过仓库读取。",
                        sourceUrl
                ));
            }

            List<ReviewToolObservation> observations = new ArrayList<>();
            observations.add(new ReviewToolObservation(
                    "github-pr-snapshot",
                    "GitHub PR 固定版本",
                    "repository=" + repository + ", pull=" + pullNumber + ", headSha=" + headSha,
                    sourceUrl
            ));

            if (parsedDiff.hasProductionJavaChange()) {
                observations.add(new ReviewToolObservation(
                        "github_read_changed_file",
                        "可按需读取 PR 变更文件",
                        "LLM 可在需要时读取 Diff 中的 Java 文件；服务端将路径限制为本次变更且固定为 head SHA。",
                        null
                ));
            }
            return observations;
        } catch (RuntimeException exception) {
            return List.of(new ReviewToolObservation(
                    "github-pr-snapshot",
                    "GitHub 上下文读取失败",
                    "无法读取 PR 固定版本上下文：" + exception.getClass().getSimpleName(),
                    sourceUrl
            ));
        }
    }

    /**
     * 向模型暴露最小只读工具面。路径仍由服务端验证，模型传入的参数不具备访问权限。
     */
    public List<ToolSpecification> availableTools(ReviewContext context) {
        return snapshot(context).isPresent()
                ? List.of(ToolSpecification.builder()
                        .name(READ_CHANGED_FILE_TOOL)
                        .description("读取本次 GitHub PR Diff 中一个 Java 变更文件在固定提交版本的完整上下文。")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("path", "必须是本次 Diff 内的 Java 文件路径")
                                .required("path")
                                .additionalProperties(false)
                                .build())
                        .strict(true)
                        .build())
                : List.of();
    }

    public ReviewToolObservation execute(ReviewContext context, ToolExecutionRequest request) {
        if (!READ_CHANGED_FILE_TOOL.equals(request.name())) {
            return toolError(request.name(), "工具不在白名单中");
        }

        Snapshot snapshot = snapshot(context).orElse(null);
        if (snapshot == null) {
            return toolError(request.name(), "当前 Review 没有已固定的 GitHub PR 版本");
        }

        try {
            JsonNode arguments = objectMapper.readTree(request.arguments());
            String path = arguments.path("path").asText();
            if (!isAllowedChangedJavaPath(context.parsedDiff(), path)) {
                return toolError(request.name(), "path 必须是本次 Diff 中的 Java 文件");
            }
            String content = readFile(snapshot.repository(), path, snapshot.headSha());
            if (content == null || content.isBlank()) {
                return toolError(request.name(), "GitHub 未返回可读取的 UTF-8 文本文件");
            }
            return new ReviewToolObservation(
                    READ_CHANGED_FILE_TOOL,
                    "GitHub 变更文件上下文：" + path,
                    "固定版本 " + snapshot.headSha().substring(0, 12) + "，内容最多 " + MAX_CONTENT_CHARS + " 字符。",
                    clip(content)
            );
        } catch (Exception exception) {
            return toolError(request.name(), "工具参数或 GitHub 读取失败：" + exception.getClass().getSimpleName());
        }
    }

    private static boolean isAllowedChangedJavaPath(ParsedDiff parsedDiff, String path) {
        return path != null
                && SAFE_PATH.matcher(path).matches()
                && parsedDiff.files().stream()
                        .map(ChangedFile::displayPath)
                        .anyMatch(changedPath -> changedPath.equals(path))
                && parsedDiff.files().stream()
                        .filter(file -> file.displayPath().equals(path))
                        .anyMatch(file -> file.fileKind() == FileKind.JAVA);
    }

    private java.util.Optional<Snapshot> snapshot(ReviewContext context) {
        return context.contextSnapshot().observations().stream()
                .filter(observation -> "github-pr-snapshot".equals(observation.toolName()))
                .map(ReviewToolObservation::detail)
                .map(Snapshot::parse)
                .flatMap(java.util.Optional::stream)
                .findFirst();
    }

    private ReviewToolObservation toolError(String toolName, String detail) {
        return new ReviewToolObservation(toolName, "工具调用被拒绝", detail, null);
    }

    private record Snapshot(String repository, String headSha) {
        private static final Pattern DETAIL = Pattern.compile(
                "repository=([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+), pull=\\d+, headSha=([A-Fa-f0-9]{7,64})"
        );

        static java.util.Optional<Snapshot> parse(String detail) {
            Matcher matcher = detail == null ? null : DETAIL.matcher(detail);
            return matcher != null && matcher.matches()
                    ? java.util.Optional.of(new Snapshot(matcher.group(1), matcher.group(2)))
                    : java.util.Optional.empty();
        }
    }

    private String readFile(String repository, String path, String headSha) {
        JsonNode file = getJson("https://api.github.com/repos/" + repository + "/contents/" + path + "?ref=" + headSha);
        String encoded = file.path("content").asText("").replaceAll("\\s", "");
        if (encoded.isBlank() || !"base64".equalsIgnoreCase(file.path("encoding").asText())) {
            return null;
        }
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private JsonNode getJson(String url) {
        RestClient.RequestHeadersSpec<?> request = restClient.get()
                .uri(url)
                .header(HttpHeaders.USER_AGENT, "CodeGuard-Agent")
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json");
        if (githubToken != null && !githubToken.isBlank()) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken);
        }
        String body = request.retrieve().body(String.class);
        try {
            return objectMapper.readTree(body);
        } catch (Exception exception) {
            throw new IllegalStateException("GitHub returned invalid JSON", exception);
        }
    }

    private static String clip(String value) {
        return value.length() <= MAX_CONTENT_CHARS
                ? value
                : value.substring(0, MAX_CONTENT_CHARS) + "\n... [truncated by CodeGuard]";
    }
}
