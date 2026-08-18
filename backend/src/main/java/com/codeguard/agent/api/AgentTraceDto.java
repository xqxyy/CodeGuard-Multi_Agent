package com.codeguard.agent.api;

import com.codeguard.agent.domain.AgentStatus;
import com.codeguard.agent.domain.AgentType;
import java.time.Instant;
import java.util.UUID;

/**
 * Agent Trace 返回对象。
 *
 * 企业演示里 Trace 很关键：它说明每个 Agent 为什么运行、运行多久、模型原始输出是什么。
 */
public record AgentTraceDto(
        UUID id,
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
) {}
