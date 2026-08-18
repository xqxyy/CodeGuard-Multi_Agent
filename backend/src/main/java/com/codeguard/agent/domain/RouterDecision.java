/** RouterAgent 的路由结果 */
package com.codeguard.agent.domain;

import java.util.Map;
import java.util.Set;

public record RouterDecision(
        Set<AgentType> enabledAgents,       /** enabledAgents 表示要运行哪些 Agent */
        Map<AgentType, String> runReasons,
        Map<AgentType, String> skipReasons
) {
    public RouterDecision {
        enabledAgents = Set.copyOf(enabledAgents);
        runReasons = Map.copyOf(runReasons);
        skipReasons = Map.copyOf(skipReasons);
    }

    public boolean shouldRun(AgentType agentType) {
        return enabledAgents.contains(agentType);
    }

    public String reasonFor(AgentType agentType) {
        if (shouldRun(agentType)) {
            return runReasons.getOrDefault(agentType, "Router selected this agent.");
        }
        return skipReasons.getOrDefault(agentType, "Router skipped this agent.");
    }
}