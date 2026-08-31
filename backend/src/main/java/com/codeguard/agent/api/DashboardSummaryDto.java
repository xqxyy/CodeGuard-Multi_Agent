package com.codeguard.agent.api;

import java.util.List;
import java.util.Map;

public record DashboardSummaryDto(
        String organizationKey,
        long projectCount,
        long repositoryCount,
        long totalReviews,
        Map<String, Long> statusCounts,
        double averageRiskScore,
        long highRiskReviews,
        long p0Issues,
        long p1Issues,
        double blockRate,
        List<ReviewListItem> latestReviews
) {}
