package com.codeguard.agent.domain;

/**
 * 上下文工具的一次观察结果。
 *
 * 这些结果会进入 Trace 和 LLM prompt，让审查不再只依赖原始 diff。
 */
public record ReviewToolObservation(
        String toolName,
        String title,
        String detail,
        String evidence
) {}
