package com.codeguard.agent.service;

import com.codeguard.agent.api.AgentTraceDto;
import com.codeguard.agent.api.DiffSummaryDto;
import com.codeguard.agent.api.ReviewIssueDto;
import com.codeguard.agent.api.ReviewListItem;
import com.codeguard.agent.api.ReviewResponse;
import com.codeguard.agent.domain.DiffSummary;
import com.codeguard.agent.persistence.AgentTraceEntity;
import com.codeguard.agent.persistence.ReviewEntity;
import com.codeguard.agent.persistence.ReviewIssueEntity;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Review 映射器
 *
 * 负责把数据库实体转换成接口返回 DTO
 * 这样 Controller 和 Service 不需要手写大量转换逻辑
 */
@Component
public class ReviewMapper {

    /**
     * 转换 Review 详情
     */
    public ReviewResponse toResponse(
            ReviewEntity review,
            List<ReviewIssueEntity> issues,
            List<AgentTraceEntity> traces,
            DiffSummary diffSummary
    ) {
        return new ReviewResponse(
                review.getId(),
                review.getTitle(),
                review.getStatus(),
                review.getProjectKey(),
                review.getRepositoryName(),
                review.getSourceType(),
                review.getSourceUrl(),
                toDto(diffSummary),
                issues.stream().map(this::toIssueDto).toList(),
                traces.stream().map(this::toTraceDto).toList(),
                review.getRecommendation(),
                review.getRiskScore(),
                review.getMarkdown(),
                review.getErrorMessage(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    /**
     * 转换历史列表项
     */
    public ReviewListItem toListItem(ReviewEntity review) {
        return new ReviewListItem(
                review.getId(),
                review.getTitle(),
                review.getStatus(),
                review.getProjectKey(),
                review.getRepositoryName(),
                review.getSourceType(),
                review.getRecommendation(),
                review.getRiskScore(),
                review.getFilesChanged(),
                review.getAdditions(),
                review.getDeletions(),
                review.getCreatedAt()
        );
    }

    /**
     * 转换 diff 摘要
     */
    public DiffSummaryDto toDto(DiffSummary summary) {
        Map<String, Long> filesByKind = summary.filesByKind()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));

        return new DiffSummaryDto(
                summary.filesChanged(),
                summary.additions(),
                summary.deletions(),
                filesByKind
        );
    }

    private ReviewIssueDto toIssueDto(ReviewIssueEntity issue) {
        return new ReviewIssueDto(
                issue.getId(),
                issue.getAgentType(),
                issue.getTag(),
                issue.getSeverity(),
                issue.getFilePath(),
                issue.getLineNumber(),
                issue.getTitle(),
                issue.getDetail(),
                issue.getSuggestion(),
                issue.getEvidence()
        );
    }

    private AgentTraceDto toTraceDto(AgentTraceEntity trace) {
        return new AgentTraceDto(
                trace.getId(),
                trace.getAgentType(),
                trace.getStatus(),
                trace.getInputSummary(),
                trace.getOutputSummary(),
                trace.getSkipReason(),
                trace.getDurationMs(),
                trace.getStartedAt(),
                trace.getEndedAt(),
                trace.getPrompt(),
                trace.getRawOutput(),
                trace.getModelName(),
                trace.getProvider(),
                trace.getPromptTokens(),
                trace.getCompletionTokens(),
                trace.getErrorMessage()
        );
    }
}
