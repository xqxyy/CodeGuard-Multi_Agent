package com.codeguard.agent.service;

import com.codeguard.agent.api.ReviewIssueDto;
import com.codeguard.agent.api.ReviewResponse;
import com.codeguard.agent.domain.Severity;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * SARIF 导出服务。
 *
 * SARIF 是很多代码扫描平台能识别的结果格式，适合企业演示“审查结果可被外部平台消费”。
 */
@Service
public class SarifExportService {

    private final ReviewWorkflowService reviewWorkflowService;

    public SarifExportService(ReviewWorkflowService reviewWorkflowService) {
        this.reviewWorkflowService = reviewWorkflowService;
    }

    public Map<String, Object> export(String reviewId) {
        ReviewResponse review = reviewWorkflowService.get(java.util.UUID.fromString(reviewId));

        List<Map<String, Object>> results = review.issues()
                .stream()
                .map(this::toResult)
                .toList();

        return Map.of(
                "version", "2.1.0",
                "$schema", "https://json.schemastore.org/sarif-2.1.0.json",
                "runs", List.of(Map.of(
                        "tool", Map.of(
                                "driver", Map.of(
                                        "name", "CodeGuard Agent",
                                        "informationUri", "https://example.local/codeguard",
                                        "rules", review.issues().stream().map(this::toRule).distinct().toList()
                                )
                        ),
                        "results", results
                ))
        );
    }

    private Map<String, Object> toRule(ReviewIssueDto issue) {
        return Map.of(
                "id", issue.tag().name() + "-" + issue.severity().name(),
                "name", issue.tag().name(),
                "shortDescription", Map.of("text", issue.title()),
                "defaultConfiguration", Map.of("level", sarifLevel(issue.severity()))
        );
    }

    private Map<String, Object> toResult(ReviewIssueDto issue) {
        return Map.of(
                "ruleId", issue.tag().name() + "-" + issue.severity().name(),
                "level", sarifLevel(issue.severity()),
                "message", Map.of("text", issue.detail()),
                "locations", List.of(Map.of(
                        "physicalLocation", Map.of(
                                "artifactLocation", Map.of("uri", issue.filePath() == null ? "unknown" : issue.filePath()),
                                "region", Map.of("startLine", issue.lineNumber() == null ? 1 : issue.lineNumber())
                        )
                ))
        );
    }

    private String sarifLevel(Severity severity) {
        return switch (severity) {
            case P0, P1 -> "error";
            case P2 -> "warning";
            case P3 -> "note";
        };
    }
}
