/**
 * 路由智能体
 * 根据 diff 内容动态选择哪些 Agent 运行
 *
 * 负责做“调度判断”：
 * 这次 diff 改了什么？
 * 应该跑哪些 Agent？
 * 哪些 Agent 可以跳过？
 * 为什么跑？
 * 为什么跳过？
 */
package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.FileKind;
import com.codeguard.agent.domain.ParsedDiff;
import com.codeguard.agent.domain.RouterDecision;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RouterAgent {

    public RouterDecision route(ParsedDiff parsedDiff, String rawDiff) {
        EnumSet<AgentType> enabledAgents = EnumSet.noneOf(AgentType.class);
        Map<AgentType, String> runReasons = new EnumMap<>(AgentType.class);
        Map<AgentType, String> skipReasons = new EnumMap<>(AgentType.class);

        boolean hasJava = parsedDiff.files().stream()
                .anyMatch(file -> file.fileKind() == FileKind.JAVA);

        boolean hasTest = parsedDiff.files().stream()
                .anyMatch(file -> file.fileKind() == FileKind.TEST);

        boolean hasSql = parsedDiff.files().stream()
                .anyMatch(file -> file.fileKind() == FileKind.SQL);

        boolean hasConfig = parsedDiff.files().stream()
                .anyMatch(file -> file.fileKind() == FileKind.CONFIG);

        boolean hasBuild = parsedDiff.files().stream()
                .anyMatch(file -> file.fileKind() == FileKind.BUILD);

        boolean onlyMarkdown = !parsedDiff.files().isEmpty()
                && parsedDiff.files().stream()
                .allMatch(file -> file.fileKind() == FileKind.MARKDOWN);

        String lowerDiff = rawDiff == null ? "" : rawDiff.toLowerCase(Locale.ROOT);

        boolean hasSecuritySignal = hasSql
                || hasConfig
                || lowerDiff.contains("auth")
                || lowerDiff.contains("password")
                || lowerDiff.contains("secret")
                || lowerDiff.contains("token")
                || lowerDiff.contains("permitall")
                || lowerDiff.contains("csrf")
                || lowerDiff.contains("upload")
                || lowerDiff.contains("select ")
                || lowerDiff.contains("delete ")
                || lowerDiff.contains("update ");

        if (parsedDiff.files().isEmpty()) {
            skipAll(skipReasons, "空 diff，不执行审查 Agent");
            return new RouterDecision(enabledAgents, runReasons, skipReasons);
        }

        if (onlyMarkdown) {
            skipAll(skipReasons, "本次只修改文档，不执行代码审查 Agent");
            return new RouterDecision(enabledAgents, runReasons, skipReasons);
        }

        if (hasJava || hasSql || hasConfig) {
            enable(enabledAgents, runReasons, AgentType.BUG_LOGIC, "检测到 Java、SQL 或配置变更");
        } else {
            skipReasons.put(AgentType.BUG_LOGIC, "未检测到逻辑缺陷相关变更");
        }

        if (hasSecuritySignal) {
            enable(enabledAgents, runReasons, AgentType.SECURITY, "检测到安全敏感信号");
        } else {
            skipReasons.put(AgentType.SECURITY, "未检测到安全敏感信号");
        }

        if (hasJava || hasTest || hasBuild || hasConfig) {
            enable(enabledAgents, runReasons, AgentType.CODE_QUALITY, "检测到代码、测试、构建或配置变更");
        } else {
            skipReasons.put(AgentType.CODE_QUALITY, "未检测到需要质量审查的变更");
        }

        if (parsedDiff.hasProductionJavaChange() || hasSql) {
            enable(enabledAgents, runReasons, AgentType.TEST_COVERAGE, "检测到生产代码或 SQL 变更");
        } else {
            skipReasons.put(AgentType.TEST_COVERAGE, "未检测到生产代码或 SQL 变更");
        }

        return new RouterDecision(enabledAgents, runReasons, skipReasons);
    }

    private static void enable(
            EnumSet<AgentType> enabledAgents,
            Map<AgentType, String> runReasons,
            AgentType agentType,
            String reason
    ) {
        enabledAgents.add(agentType);
        runReasons.put(agentType, reason);
    }

    private static void skipAll(Map<AgentType, String> skipReasons, String reason) {
        skipReasons.put(AgentType.BUG_LOGIC, reason);
        skipReasons.put(AgentType.SECURITY, reason);
        skipReasons.put(AgentType.CODE_QUALITY, reason);
        skipReasons.put(AgentType.TEST_COVERAGE, reason);
    }
}