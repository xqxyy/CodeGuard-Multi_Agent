/** 表示Agent 发现的一条问题 */
package com.codeguard.agent.domain;

public record ReviewFinding(
        AgentType agentType,
        IssueTag tag,
        Severity severity,
        String filePath,
        Integer lineNumber,
        String title,
        String detail,
        String suggestion,
        String evidence
) {}