package com.codeguard.agent.api;

import com.codeguard.agent.domain.Severity;
import java.time.Instant;
import java.util.UUID;

public record ReviewPolicyDto(
        UUID id,
        String organizationKey,
        String projectKey,
        String name,
        Severity blockSeverity,
        boolean failOnP0,
        boolean requireTestsForApiChanges,
        boolean enableBugLogic,
        boolean enableSecurity,
        boolean enableCodeQuality,
        boolean enableTestCoverage,
        boolean enableLlmReview,
        int maxDiffChars,
        Instant updatedAt
) {}
