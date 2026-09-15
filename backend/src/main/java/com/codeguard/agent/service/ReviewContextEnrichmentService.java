package com.codeguard.agent.service;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ChangedFile;
import com.codeguard.agent.domain.FileKind;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ParsedDiff;
import com.codeguard.agent.domain.ReviewContextSnapshot;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.ReviewKnowledgeSnippet;
import com.codeguard.agent.domain.ReviewToolObservation;
import com.codeguard.agent.domain.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 仓库上下文工具编排服务。
 *
 * 它把 diff 变成 Agent 可消费的上下文观察结果：变更文件类型、关键路径、测试配套、
 * 依赖/配置变更和企业知识库命中。
 */
@Service
public class ReviewContextEnrichmentService {

    private final EnterpriseKnowledgeBaseService knowledgeBaseService;
    private final GithubRepositoryContextService githubRepositoryContextService;

    public ReviewContextEnrichmentService(
            EnterpriseKnowledgeBaseService knowledgeBaseService,
            GithubRepositoryContextService githubRepositoryContextService
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.githubRepositoryContextService = githubRepositoryContextService;
    }

    public ReviewContextSnapshot enrich(ParsedDiff parsedDiff, String rawDiff) {
        return enrich(parsedDiff, rawDiff, null);
    }

    public ReviewContextSnapshot enrich(ParsedDiff parsedDiff, String rawDiff, String sourceUrl) {
        List<ReviewToolObservation> observations = new ArrayList<>();
        List<ReviewFinding> findings = new ArrayList<>();

        boolean hasTestChange = parsedDiff.files().stream().anyMatch(file -> file.fileKind() == FileKind.TEST);
        boolean hasProductionJava = false;
        int highRiskPathCount = 0;

        for (ChangedFile file : parsedDiff.files()) {
            String path = file.displayPath();
            String lowerPath = path.toLowerCase(Locale.ROOT);

            observations.add(new ReviewToolObservation(
                    "changed-file-index",
                    path,
                    "文件类型=" + file.fileKind() + ", hunks=" + file.hunks().size(),
                    path
            ));

            if (file.fileKind() == FileKind.JAVA && !lowerPath.contains("/test/")) {
                hasProductionJava = true;
                if (!hasTestChange && isApplicationBoundary(path)) {
                    findings.add(new ReviewFinding(
                            AgentType.CONTEXT_ENRICHMENT,
                            IssueTag.TEST_GAP,
                            Severity.P2,
                            path,
                            null,
                            "关键生产代码变更缺少同批测试",
                            "上下文工具发现 Controller、Service、Repository 或安全边界变更，但本次 diff 没有测试文件变更。",
                            "补充对应单元测试、MockMvc 测试或安全回归测试，再合并。",
                            "changedFile=" + path
                    ));
                }
            }

            if (isHighRiskPath(lowerPath)) {
                highRiskPathCount++;
                observations.add(new ReviewToolObservation(
                        "risk-path-classifier",
                        "高风险路径命中",
                        "该文件位于认证、配置、数据库迁移、构建依赖或部署相关路径。",
                        path
                ));
            }
        }

        observations.addAll(githubRepositoryContextService.collect(sourceUrl, parsedDiff));

        List<ReviewKnowledgeSnippet> snippets = knowledgeBaseService.retrieve(parsedDiff, rawDiff);
        String summary = "productionJava="
                + hasProductionJava
                + ", testChanged="
                + hasTestChange
                + ", highRiskPaths="
                + highRiskPathCount
                + ", knowledgeHits="
                + snippets.size()
                + ", toolObservations="
                + observations.size();

        return new ReviewContextSnapshot(summary, observations, snippets, findings);
    }

    private static boolean isApplicationBoundary(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("controller")
                || lower.contains("service")
                || lower.contains("repository")
                || lower.contains("security")
                || lower.contains("config");
    }

    private static boolean isHighRiskPath(String lowerPath) {
        return lowerPath.contains("security")
                || lowerPath.contains("auth")
                || lowerPath.contains("migration")
                || lowerPath.endsWith("pom.xml")
                || lowerPath.endsWith("package.json")
                || lowerPath.endsWith("dockerfile")
                || lowerPath.endsWith("application.yml")
                || lowerPath.endsWith("application.yaml")
                || lowerPath.endsWith(".sql");
    }
}
