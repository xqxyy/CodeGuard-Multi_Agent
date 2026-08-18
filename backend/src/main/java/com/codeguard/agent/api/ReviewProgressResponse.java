package com.codeguard.agent.api;

import com.codeguard.agent.domain.MergeRecommendation;
import com.codeguard.agent.domain.ReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 审查进度返回对象。
 *
 * 前端可以用它展示每个 Agent 的运行状态，而不是只看到最终结果。
 */
public record ReviewProgressResponse(
        UUID id,
        String title,
        ReviewStatus status,
        int totalAgents,
        int completedAgents,
        int failedAgents,
        int skippedAgents,
        MergeRecommendation recommendation,
        int riskScore,
        List<AgentTraceDto> traces,
        Instant updatedAt
) {}
