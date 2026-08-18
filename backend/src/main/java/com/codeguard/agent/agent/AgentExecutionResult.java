/** 表示一个 Agent 的执行结果 */
package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ReviewFinding;
import java.util.List;

public record AgentExecutionResult(
        AgentType agentType,
        List<ReviewFinding> findings,
        String outputSummary
) {
    public AgentExecutionResult {
        findings = List.copyOf(findings);
    }
}