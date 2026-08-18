/** 表示 Agent 的运行轨迹 */
package com.codeguard.agent.domain;

import java.time.Instant;

public record AgentTraceRecord(
        AgentType agentType,
        AgentStatus status,
        String inputSummary,
        String outputSummary,
        String skipReason,
        long durationMs,
        Instant startedAt,
        Instant endedAt,
        String prompt,
        String rawOutput,
        String modelName,
        String provider,
        Integer promptTokens,
        Integer completionTokens,
        String errorMessage
) {
    /**
     * 兼容普通规则 Agent 的简化构造方法。
     *
     * 规则 Agent 没有 prompt、模型名、token 用量，所以这些审计字段默认留空。
     */
    public AgentTraceRecord(
            AgentType agentType,
            AgentStatus status,
            String inputSummary,
            String outputSummary,
            String skipReason,
            long durationMs,
            Instant startedAt,
            Instant endedAt
    ) {
        this(agentType, status, inputSummary, outputSummary, skipReason, durationMs,
                startedAt, endedAt, null, null, null, null, null, null, null);
    }
}
