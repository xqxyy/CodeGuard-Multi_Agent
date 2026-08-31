package com.codeguard.agent.domain;

import java.util.List;

/**
 * Agent 工具和企业知识库生成的上下文快照。
 */
public record ReviewContextSnapshot(
        String summary,
        List<ReviewToolObservation> observations,
        List<ReviewKnowledgeSnippet> knowledgeSnippets,
        List<ReviewFinding> contextFindings
) {
    public ReviewContextSnapshot {
        observations = List.copyOf(observations == null ? List.of() : observations);
        knowledgeSnippets = List.copyOf(knowledgeSnippets == null ? List.of() : knowledgeSnippets);
        contextFindings = List.copyOf(contextFindings == null ? List.of() : contextFindings);
        summary = summary == null ? "" : summary;
    }

    public static ReviewContextSnapshot empty() {
        return new ReviewContextSnapshot("", List.of(), List.of(), List.of());
    }
}
