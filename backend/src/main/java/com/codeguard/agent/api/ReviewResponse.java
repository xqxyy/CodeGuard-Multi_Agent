package com.codeguard.agent.api;

import com.codeguard.agent.domain.MergeRecommendation;
import com.codeguard.agent.domain.ReviewSourceType;
import com.codeguard.agent.domain.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Review 详情返回对象。
 *
 * 聚合 Review 摘要、问题列表、Agent Trace、Markdown 报告和企业维度元数据。
 */
public record ReviewResponse(
        UUID id,
        String title,
        ReviewStatus status,
        String projectKey,
        String repositoryName,
        ReviewSourceType sourceType,
        String sourceUrl,
        DiffSummaryDto diffSummary,
        List<ReviewIssueDto> issues,
        List<AgentTraceDto> traces,
        MergeRecommendation recommendation,
        int riskScore,
        String markdown,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {}
