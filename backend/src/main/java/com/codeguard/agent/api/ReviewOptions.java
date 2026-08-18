package com.codeguard.agent.api;

/**
 * 单次审查的 Agent 开关。
 *
 * 使用 Boolean 而不是 boolean，是为了区分“前端没传”和“明确传 false”。
 */
public record ReviewOptions(
        Boolean enableBugLogic,
        Boolean enableSecurity,
        Boolean enableCodeQuality,
        Boolean enableTestCoverage,
        Boolean enableLlmReview,
        Boolean failOnP0
) {
    public static ReviewOptions defaults() {
        return new ReviewOptions(true, true, true, true, true, true);
    }

    public ReviewOptions withDefaults() {
        ReviewOptions defaults = defaults();
        return new ReviewOptions(
                enableBugLogic == null ? defaults.enableBugLogic : enableBugLogic,
                enableSecurity == null ? defaults.enableSecurity : enableSecurity,
                enableCodeQuality == null ? defaults.enableCodeQuality : enableCodeQuality,
                enableTestCoverage == null ? defaults.enableTestCoverage : enableTestCoverage,
                enableLlmReview == null ? defaults.enableLlmReview : enableLlmReview,
                failOnP0 == null ? defaults.failOnP0 : failOnP0
        );
    }

    public boolean isEnabled(String agentType) {
        return switch (agentType) {
            case "BUG_LOGIC" -> Boolean.TRUE.equals(enableBugLogic);
            case "SECURITY" -> Boolean.TRUE.equals(enableSecurity);
            case "CODE_QUALITY" -> Boolean.TRUE.equals(enableCodeQuality);
            case "TEST_COVERAGE" -> Boolean.TRUE.equals(enableTestCoverage);
            case "LLM_REVIEW" -> Boolean.TRUE.equals(enableLlmReview);
            default -> true;
        };
    }
}
