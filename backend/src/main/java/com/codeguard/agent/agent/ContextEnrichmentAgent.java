package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ReviewContext;

import org.springframework.stereotype.Component;

/**
 * 上下文工具 Agent。
 *
 * 真正执行工具编排的是 ReviewContextEnrichmentService；这个 Agent 负责把工具发现纳入统一问题流。
 */
@Component
public class ContextEnrichmentAgent implements ReviewAgent {

    @Override
    public AgentType type() {
        return AgentType.CONTEXT_ENRICHMENT;
    }

    @Override
    public AgentExecutionResult review(ReviewContext context) {
        var snapshot = context.contextSnapshot();
        return new AgentExecutionResult(
                type(),
                snapshot.contextFindings(),
                "上下文工具完成："
                        + snapshot.summary()
                        + ", observations="
                        + snapshot.observations().size()
        );
    }
}
