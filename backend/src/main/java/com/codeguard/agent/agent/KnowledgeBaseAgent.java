package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.ReviewKnowledgeSnippet;
import com.codeguard.agent.domain.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * 企业知识库 Agent。
 *
 * 把知识库检索命中的规范转成可执行审查建议。
 */
@Component
public class KnowledgeBaseAgent implements ReviewAgent {

    @Override
    public AgentType type() {
        return AgentType.KNOWLEDGE_BASE;
    }

    @Override
    public AgentExecutionResult review(ReviewContext context) {
        List<ReviewFinding> findings = new ArrayList<>();
        String lowerDiff = context.rawDiff() == null ? "" : context.rawDiff().toLowerCase(Locale.ROOT);

        for (ReviewKnowledgeSnippet snippet : context.contextSnapshot().knowledgeSnippets()) {
            if ("authz-boundary".equals(snippet.id())
                    && (lowerDiff.contains("-@preauthorize") || lowerDiff.contains("permitall()"))) {
                findings.add(policyFinding(
                        IssueTag.SECURITY,
                        Severity.P1,
                        "权限边界变更缺少企业规范校验",
                        "知识库命中权限边界规范：授权注解删除或 permitAll 变更必须补充越权访问测试。",
                        "补充未登录、低权限用户和跨租户访问测试，并在审计日志中记录权限变更。",
                        snippet
                ));
            }

            if ("dependency-governance".equals(snippet.id())
                    && (lowerDiff.contains("+        <dependency>") || lowerDiff.contains("+  \"dependencies\""))) {
                findings.add(policyFinding(
                        IssueTag.QUALITY,
                        Severity.P2,
                        "新增依赖缺少供应链治理说明",
                        "知识库命中依赖治理规范：新增依赖需要版本固定、用途说明、安全漏洞和许可证检查。",
                        "在 PR 描述或审查报告中补充依赖用途，并运行依赖漏洞扫描。",
                        snippet
                ));
            }

            if ("queue-reliability".equals(snippet.id())
                    && (lowerDiff.contains("@async") || lowerDiff.contains("@scheduled") || lowerDiff.contains("executor"))) {
                findings.add(policyFinding(
                        IssueTag.BUG,
                        Severity.P2,
                        "异步任务变更需要可靠性兜底",
                        "知识库命中异步任务可靠性规范：任务需要幂等、超时、重试和失败可观测。",
                        "确认任务有重复执行保护、超时恢复、失败 Trace 和指标监控。",
                        snippet
                ));
            }
        }

        return new AgentExecutionResult(
                type(),
                findings,
                "KnowledgeBaseAgent 命中 "
                        + context.contextSnapshot().knowledgeSnippets().size()
                        + " 条企业知识，生成 "
                        + findings.size()
                        + " 个规范风险"
        );
    }

    private ReviewFinding policyFinding(
            IssueTag tag,
            Severity severity,
            String title,
            String detail,
            String suggestion,
            ReviewKnowledgeSnippet snippet
    ) {
        return new ReviewFinding(
                type(),
                tag,
                severity,
                null,
                null,
                title,
                detail,
                suggestion,
                snippet.source() + " / " + snippet.title()
        );
    }
}
