package com.codeguard.agent.agent;

import com.codeguard.agent.config.CodeGuardChatModelProvider;
import com.codeguard.agent.domain.AgentStatus;
import com.codeguard.agent.domain.AgentTraceRecord;
import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ChangedFile;
import com.codeguard.agent.domain.DiffLineType;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.Severity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * LLM 审查 Agent。
 *
 * 它负责调用兼容 OpenAI 协议的大模型，并把模型 JSON 输出转换成统一的 ReviewFinding。
 */
@Component
public class LlmReviewAgent {

    private final CodeGuardChatModelProvider chatModelProvider;
    private final ObjectMapper objectMapper;

    /** LangChain4j 创建的代理对象可以复用，避免每次 Review 都重新生成。 */
    private CodeReviewAiAgent aiAgent;

    public LlmReviewAgent(
            CodeGuardChatModelProvider chatModelProvider,
            ObjectMapper objectMapper
    ) {
        this.chatModelProvider = chatModelProvider;
        this.objectMapper = objectMapper;
    }

    public LlmReviewRun review(ReviewContext context, List<ReviewFinding> ruleFindings) {
        Instant startedAt = Instant.now();

        Optional<ChatModel> chatModel = chatModelProvider.chatModel();
        if (chatModel.isEmpty()) {
            return skipped(startedAt, "没有配置可用的 LLM API Key，跳过 LLM 审查");
        }

        String diffSummary = diffSummary(context);
        String ruleFindingsJson = "[]";
        String diffSnippet = diffSnippet(context);

        try {
            ruleFindingsJson = objectMapper.writeValueAsString(ruleFindings);
            String promptAudit = promptAudit(diffSummary, ruleFindingsJson, diffSnippet);

            String rawJson = agent(chatModel.get()).review(diffSummary, ruleFindingsJson, diffSnippet);
            List<ReviewFinding> findings = parseFindings(rawJson);

            Instant endedAt = Instant.now();
            AgentTraceRecord trace = new AgentTraceRecord(
                    AgentType.LLM_REVIEW,
                    AgentStatus.COMPLETED,
                    "ruleFindings=" + ruleFindings.size(),
                    "LLM 额外发现 " + findings.size() + " 个问题",
                    null,
                    Duration.between(startedAt, endedAt).toMillis(),
                    startedAt,
                    endedAt,
                    promptAudit,
                    rawJson,
                    chatModelProvider.modelName(),
                    chatModelProvider.providerName(),
                    null,
                    null,
                    null
            );

            return new LlmReviewRun(findings, trace);
        } catch (Exception exception) {
            Instant endedAt = Instant.now();
            AgentTraceRecord trace = new AgentTraceRecord(
                    AgentType.LLM_REVIEW,
                    AgentStatus.FAILED,
                    "ruleFindings=" + ruleFindings.size(),
                    "LLM 审查失败：" + exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    Duration.between(startedAt, endedAt).toMillis(),
                    startedAt,
                    endedAt,
                    promptAudit(diffSummary, ruleFindingsJson, diffSnippet),
                    null,
                    chatModelProvider.modelName(),
                    chatModelProvider.providerName(),
                    null,
                    null,
                    exception.getMessage()
            );

            return new LlmReviewRun(List.of(), trace);
        }
    }

    private CodeReviewAiAgent agent(ChatModel chatModel) {
        if (aiAgent == null) {
            aiAgent = AgenticServices.agentBuilder(CodeReviewAiAgent.class)
                    .chatModel(chatModel)
                    .outputKey("llmReviewJson")
                    .build();
        }

        return aiAgent;
    }

    private LlmReviewRun skipped(Instant startedAt, String reason) {
        Instant endedAt = Instant.now();
        AgentTraceRecord trace = new AgentTraceRecord(
                AgentType.LLM_REVIEW,
                AgentStatus.SKIPPED,
                "LLM review requested",
                null,
                reason,
                Duration.between(startedAt, endedAt).toMillis(),
                startedAt,
                endedAt,
                null,
                null,
                chatModelProvider.modelName(),
                chatModelProvider.providerName(),
                null,
                null,
                null
        );

        return new LlmReviewRun(List.of(), trace);
    }

    /**
     * 解析并校验模型 JSON。
     *
     * 如果模型输出不是合法 JSON，调用方会记录 FAILED Trace，而不是让整次 Review 崩掉。
     */
    private List<ReviewFinding> parseFindings(String rawJson) throws Exception {
        String json = extractJson(rawJson);
        JsonNode root = objectMapper.readTree(json);
        JsonNode findingsNode = root.path("findings");

        if (!findingsNode.isArray()) {
            return List.of();
        }

        List<ReviewFinding> findings = new ArrayList<>();

        for (JsonNode node : findingsNode) {
            if (findings.size() >= 5) {
                break;
            }

            findings.add(new ReviewFinding(
                    AgentType.LLM_REVIEW,
                    enumValue(IssueTag.class, node.path("tag").asText(), IssueTag.QUALITY),
                    enumValue(Severity.class, node.path("severity").asText(), Severity.P3),
                    nullIfBlank(node.path("filePath").asText(null)),
                    node.path("lineNumber").canConvertToInt() ? node.path("lineNumber").asInt() : null,
                    textOrDefault(node, "title", "LLM 审查发现"),
                    textOrDefault(node, "detail", "LLM 发现了额外风险"),
                    textOrDefault(node, "suggestion", "请在合并前确认这个风险"),
                    nullIfBlank(node.path("evidence").asText(null))
            ));
        }

        return findings;
    }

    private String extractJson(String value) {
        String text = value == null ? "" : value.strip();

        if (text.startsWith("```")) {
            int firstBrace = text.indexOf('{');
            int lastBrace = text.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return text.substring(firstBrace, lastBrace + 1);
            }
        }

        return text;
    }

    private String diffSummary(ReviewContext context) {
        return "files="
                + context.parsedDiff().summary().filesChanged()
                + ", additions="
                + context.parsedDiff().summary().additions()
                + ", deletions="
                + context.parsedDiff().summary().deletions()
                + ", fileKinds="
                + context.parsedDiff().summary().filesByKind();
    }

    private String diffSnippet(ReviewContext context) {
        StringBuilder builder = new StringBuilder();

        for (ChangedFile file : context.parsedDiff().files()) {
            builder.append("FILE ")
                    .append(file.displayPath())
                    .append(" [")
                    .append(file.fileKind())
                    .append("]\n");

            file.hunks().forEach(hunk ->
                    hunk.lines().stream()
                            .filter(line -> line.type() == DiffLineType.ADDITION
                                    || line.type() == DiffLineType.DELETION)
                            .forEach(line -> builder
                                    .append(line.type() == DiffLineType.ADDITION ? "+" : "-")
                                    .append(line.type() == DiffLineType.ADDITION
                                            ? line.newLineNumber()
                                            : line.oldLineNumber())
                                    .append(": ")
                                    .append(line.content())
                                    .append("\n"))
            );
        }

        return builder.toString();
    }

    private String promptAudit(String diffSummary, String ruleFindingsJson, String diffSnippet) {
        String audit = """
                diffSummary:
                %s

                ruleFindingsJson:
                %s

                diffSnippet:
                %s
                """.formatted(diffSummary, ruleFindingsJson, diffSnippet);

        return audit.length() > 12000 ? audit.substring(0, 12000) + "\n...truncated" : audit;
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String nullIfBlank(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }

        return value.strip();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    public record LlmReviewRun(List<ReviewFinding> findings, AgentTraceRecord trace) {
        public LlmReviewRun {
            findings = List.copyOf(findings);
        }
    }
}
