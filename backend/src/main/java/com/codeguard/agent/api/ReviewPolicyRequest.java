package com.codeguard.agent.api;

import com.codeguard.agent.domain.Severity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ReviewPolicyRequest(
        @Size(max = 160, message = "name must be at most 160 characters")
        String name,

        Severity blockSeverity,
        Boolean failOnP0,
        Boolean requireTestsForApiChanges,
        Boolean enableBugLogic,
        Boolean enableSecurity,
        Boolean enableCodeQuality,
        Boolean enableTestCoverage,
        Boolean enableLlmReview,

        @Min(value = 1000, message = "maxDiffChars must be at least 1000")
        @Max(value = 1000000, message = "maxDiffChars must be at most 1000000")
        Integer maxDiffChars
) {}
