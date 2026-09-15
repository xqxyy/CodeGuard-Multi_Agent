/** 一次 Review 过程中传给 Agent 的上下文 */
package com.codeguard.agent.domain;

public record ReviewContext(
        String title,
        String rawDiff,
        ParsedDiff parsedDiff,
        RouterDecision routerDecision,
        ReviewContextSnapshot contextSnapshot,
        String sourceUrl
) {
    public ReviewContext(String title, String rawDiff, ParsedDiff parsedDiff, RouterDecision routerDecision) {
        this(title, rawDiff, parsedDiff, routerDecision, ReviewContextSnapshot.empty(), null);
    }

    public ReviewContext(
            String title,
            String rawDiff,
            ParsedDiff parsedDiff,
            RouterDecision routerDecision,
            ReviewContextSnapshot contextSnapshot
    ) {
        this(title, rawDiff, parsedDiff, routerDecision, contextSnapshot, null);
    }
}
