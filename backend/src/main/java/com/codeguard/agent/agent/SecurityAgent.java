/** 安全审查智能体
 *
 * 负责检查：
 * SQL 注入
 * 硬编码密钥
 * 敏感日志
 * 放宽权限配置
 */
package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ChangedFile;
import com.codeguard.agent.domain.FileKind;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SecurityAgent implements ReviewAgent {

    private static final Pattern SQL_CONCAT =
            Pattern.compile("(?i)(select|insert|update|delete)\\b.*\\+|\\+.*\\b(from|where|values|set)\\b");

    private static final Pattern SECRET_LITERAL =
            Pattern.compile("(?i)(password|secret|token|api[_-]?key)\\s*[:=]\\s*[\"']?[A-Za-z0-9_./+=-]{6,}");

    private static final Pattern SENSITIVE_LOG =
            Pattern.compile("(?i)log\\.(info|debug|warn|error)\\s*\\(.*(password|token|secret|authorization)");

    @Override
    public AgentType type() {
        return AgentType.SECURITY;
    }

    @Override
    public AgentExecutionResult review(ReviewContext context) {
        List<ReviewFinding> findings = new ArrayList<>();

        for (AgentSupport.LineRef line : AgentSupport.addedLines(context.parsedDiff())) {
            String content = line.content();

            if (AgentSupport.matches(content, SQL_CONCAT)) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.SECURITY,
                        Severity.P0,
                        line.filePath(),
                        line.lineNumber(),
                        "SQL 拼接存在注入风险",
                        "新增 SQL 语句疑似通过字符串拼接外部输入，攻击者可能改变查询结构",
                        "使用参数化查询、MyBatis #{ }、JPA 参数绑定，禁止拼接用户输入",
                        AgentSupport.shortEvidence(content)
                ));
            }

            if (AgentSupport.matches(content, SECRET_LITERAL)) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.SECURITY,
                        Severity.P0,
                        line.filePath(),
                        line.lineNumber(),
                        "疑似硬编码密钥或凭据",
                        "代码或配置中出现 password、secret、token、apiKey 等敏感值",
                        "改为从环境变量或密钥管理服务读取，已经提交过的密钥需要轮换",
                        AgentSupport.shortEvidence(content)
                ));
            }

            if (AgentSupport.matches(content, SENSITIVE_LOG)) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.SECURITY,
                        Severity.P1,
                        line.filePath(),
                        line.lineNumber(),
                        "日志可能泄露敏感信息",
                        "新增日志包含密码、Token、Secret 或 Authorization 字段",
                        "日志中只保留脱敏后的必要字段，不打印完整凭据",
                        AgentSupport.shortEvidence(content)
                ));
            }

            if (content.contains("permitAll()") || content.contains("csrf().disable()")) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.SECURITY,
                        Severity.P1,
                        line.filePath(),
                        line.lineNumber(),
                        "安全配置被放宽",
                        "新增配置放宽了鉴权或 CSRF 防护，可能扩大接口暴露面",
                        "确认只对必要路径开放，并补充未授权访问测试",
                        AgentSupport.shortEvidence(content)
                ));
            }
        }

        if (AgentSupport.rawContains(context.rawDiff(), "-    @PreAuthorize")
                || AgentSupport.rawContains(context.rawDiff(), "-@PreAuthorize")) {
            findings.add(AgentSupport.finding(
                    type(),
                    IssueTag.SECURITY,
                    Severity.P1,
                    null,
                    null,
                    "权限注解被移除",
                    "diff 中出现授权注解删除，接口可能从受保护变为未授权可访问",
                    "确认是否有等价网关或过滤器保护，并补充越权访问回归测试",
                    "removed @PreAuthorize"
            ));
        }

        for (ChangedFile file : context.parsedDiff().files()) {
            boolean touchesUpload = AgentSupport.fileContains(file, "MultipartFile")
                    || AgentSupport.fileContains(file, "transferTo(")
                    || AgentSupport.fileContains(file, "getOriginalFilename()");

            boolean hasValidation = AgentSupport.fileContains(file, "getSize(")
                    || AgentSupport.fileContains(file, "getContentType(")
                    || AgentSupport.fileContains(file, "Path.normalize")
                    || AgentSupport.fileContains(file, "Files.probeContentType");

            if (touchesUpload && !hasValidation) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.SECURITY,
                        Severity.P1,
                        file.displayPath(),
                        null,
                        "文件上传缺少大小、类型或路径校验",
                        "新增上传逻辑但没有看到文件大小、MIME 类型、扩展名或路径归一化校验，可能导致恶意文件写入或路径穿越",
                        "限制大小和类型，生成服务端文件名，规范化目标路径，并补充恶意文件名测试",
                        "path=" + file.displayPath()
                ));
            }
        }

        return new AgentExecutionResult(
                type(),
                findings,
                "SecurityAgent 发现 " + findings.size() + " 个安全问题"
        );
    }
}
