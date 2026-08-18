package com.codeguard.agent.api;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.Severity;
import java.util.UUID;

/**
 * Review 问题返回对象
 *
 * 用于向前端展示单条审查问题
 */
public record ReviewIssueDto(
        UUID id,
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