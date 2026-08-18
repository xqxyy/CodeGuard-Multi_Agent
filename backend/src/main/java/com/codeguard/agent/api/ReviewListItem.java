package com.codeguard.agent.api;

import com.codeguard.agent.domain.MergeRecommendation;
import com.codeguard.agent.domain.ReviewSourceType;
import com.codeguard.agent.domain.ReviewStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Review 历史列表项。
 *
 * 列表页只返回摘要字段，避免一次拉取过多 Markdown 和问题详情。
 */
public record ReviewListItem(
        UUID id,
        String title,
        ReviewStatus status,
        String projectKey,
        String repositoryName,
        ReviewSourceType sourceType,
        MergeRecommendation recommendation,
        int riskScore,
        int filesChanged,
        int additions,
        int deletions,
        Instant createdAt
) {}
