package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.FileKind;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 企业静态分析 Agent。
 *
 * 这里先内置一组轻量规则，并保留和 Semgrep、SpotBugs、依赖扫描器合并的边界。
 */
@Component
public class EnterpriseStaticAnalysisAgent implements ReviewAgent {

    private static final Pattern DYNAMIC_DEPENDENCY_VERSION =
            Pattern.compile("(?i)<version>\\s*(latest|release|snapshot)\\s*</version>|\"\\s*:\\s*\"(latest|\\*)\"");

    @Override
    public AgentType type() {
        return AgentType.STATIC_ANALYSIS;
    }

    @Override
    public AgentExecutionResult review(ReviewContext context) {
        List<ReviewFinding> findings = new ArrayList<>();

        for (AgentSupport.LineRef line : AgentSupport.addedLines(context.parsedDiff())) {
            String content = line.content();
            String lower = content.toLowerCase(Locale.ROOT);

            if (line.fileKind() == FileKind.BUILD && AgentSupport.matches(content, DYNAMIC_DEPENDENCY_VERSION)) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.QUALITY,
                        Severity.P2,
                        line.filePath(),
                        line.lineNumber(),
                        "依赖版本未固定",
                        "构建文件中出现 latest、release、snapshot 或通配版本，可能导致构建不可复现。",
                        "固定明确版本，并通过依赖漏洞扫描确认供应链风险。",
                        AgentSupport.shortEvidence(content)
                ));
            }

            if (lower.contains("system.exit(")) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.BUG,
                        Severity.P1,
                        line.filePath(),
                        line.lineNumber(),
                        "服务端代码调用 System.exit",
                        "请求处理或后台任务中退出 JVM 会导致整个服务不可用。",
                        "改为抛出业务异常或返回错误状态，由框架统一处理。",
                        AgentSupport.shortEvidence(content)
                ));
            }

            if (lower.contains("new thread(") || lower.contains("executors.newcachedthreadpool")) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.QUALITY,
                        Severity.P2,
                        line.filePath(),
                        line.lineNumber(),
                        "线程资源缺少统一治理",
                        "新增裸线程或无界线程池，可能绕过平台线程池、限流和监控。",
                        "使用受控 ThreadPoolTaskExecutor，并配置队列、超时、拒绝策略和指标。",
                        AgentSupport.shortEvidence(content)
                ));
            }

            if (lower.contains("todo") && (lower.contains("security") || lower.contains("auth") || lower.contains("permission"))) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.SECURITY,
                        Severity.P2,
                        line.filePath(),
                        line.lineNumber(),
                        "安全逻辑存在 TODO 占位",
                        "安全或权限相关 TODO 被提交到生产变更中，可能代表关键控制未完成。",
                        "在合并前实现该安全控制，或明确由策略中心关闭对应入口。",
                        AgentSupport.shortEvidence(content)
                ));
            }
        }

        return new AgentExecutionResult(
                type(),
                findings,
                "EnterpriseStaticAnalysisAgent 发现 " + findings.size() + " 个企业静态分析问题"
        );
    }
}
