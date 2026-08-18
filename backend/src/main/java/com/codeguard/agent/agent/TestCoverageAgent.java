/** 测试覆盖智能体
 *
 * 检查：
 * 生产代码变更有没有测试
 * Controller 是否需要接口测试
 * Service 是否需要单测
 * SQL 是否需要边界测试
 * 权限变更是否需要安全测试
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
import org.springframework.stereotype.Component;

@Component
public class TestCoverageAgent implements ReviewAgent {

    @Override
    public AgentType type() {
        return AgentType.TEST_COVERAGE;
    }

    @Override
    public AgentExecutionResult review(ReviewContext context) {
        List<ReviewFinding> findings = new ArrayList<>();

        List<ChangedFile> productionFiles = context.parsedDiff().files().stream()
                .filter(file -> file.fileKind() == FileKind.JAVA || file.fileKind() == FileKind.SQL)
                .toList();

        if (productionFiles.isEmpty()) {
            return new AgentExecutionResult(type(), findings, "没有生产代码变更");
        }

        if (!context.parsedDiff().hasTestChange()) {
            findings.add(AgentSupport.finding(
                    type(),
                    IssueTag.TEST_GAP,
                    Severity.P2,
                    null,
                    null,
                    "生产代码变更未包含测试文件",
                    "本次变更涉及生产 Java 或 SQL 文件，但没有看到测试文件同步变化",
                    "至少补充 Service 单测、Controller 接口测试，或 SQL 参数化与边界测试",
                    "hasTestChange=false"
            ));
        }

        for (ChangedFile file : productionFiles) {
            boolean controller = file.displayPath().endsWith("Controller.java")
                    || AgentSupport.fileContains(file, "@RestController");

            boolean service = file.displayPath().endsWith("Service.java")
                    || AgentSupport.pathContains(file, "/service/");

            boolean sql = file.fileKind() == FileKind.SQL
                    || AgentSupport.fileContains(file, "JdbcTemplate")
                    || AgentSupport.fileContains(file, "@Query");

            if (controller) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.TEST_GAP,
                        Severity.P3,
                        file.displayPath(),
                        null,
                        "Controller 变更需要接口测试",
                        "接口层变更会影响 HTTP 状态码、参数校验、鉴权和响应结构",
                        "建议新增 MockMvc 用例，覆盖成功、参数错误、未授权、下游异常",
                        "path=" + file.displayPath()
                ));
            }

            if (service) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.TEST_GAP,
                        Severity.P3,
                        file.displayPath(),
                        null,
                        "Service 变更需要业务单测",
                        "Service 层通常承载事务、状态变更和异常语义",
                        "建议覆盖空输入、空查询结果、重复提交、仓储异常、事务回滚",
                        "path=" + file.displayPath()
                ));
            }

            if (sql) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.TEST_GAP,
                        Severity.P2,
                        file.displayPath(),
                        null,
                        "SQL 或查询逻辑需要边界测试",
                        "查询变更可能影响过滤条件、分页、空结果和注入面",
                        "建议补充特殊字符输入、空结果、多租户边界、分页边界测试",
                        "path=" + file.displayPath()
                ));
            }
        }

        if (AgentSupport.rawContains(context.rawDiff(), "-    @PreAuthorize")
                || AgentSupport.rawContains(context.rawDiff(), "permitAll()")) {
            findings.add(AgentSupport.finding(
                    type(),
                    IssueTag.TEST_GAP,
                    Severity.P1,
                    null,
                    null,
                    "授权逻辑变更缺少安全回归测试",
                    "权限注解删除或公开访问配置变化，需要测试证明未授权调用仍被拒绝",
                    "新增 401/403 用例、越权用户用例，以及最小权限角色访问用例",
                    "authorization change signal"
            ));
        }

        return new AgentExecutionResult(
                type(),
                findings,
                "TestCoverageAgent 发现 " + findings.size() + " 个测试缺口"
        );
    }
}