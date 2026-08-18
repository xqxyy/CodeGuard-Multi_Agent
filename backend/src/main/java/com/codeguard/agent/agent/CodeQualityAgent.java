/** 代码质量智能体
 *
 * 检查：
 * System.out.println
 * Map<String, Object>
 * Controller 直接访问 Repository
 * 单文件新增过多
 */
package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ChangedFile;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.Severity;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CodeQualityAgent implements ReviewAgent {

    @Override
    public AgentType type() {
        return AgentType.CODE_QUALITY;
    }

    @Override
    public AgentExecutionResult review(ReviewContext context) {
        List<ReviewFinding> findings = new ArrayList<>();

        for (AgentSupport.LineRef line : AgentSupport.addedLines(context.parsedDiff())) {
            String content = line.content();

            if (content.contains("System.out.println")) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.QUALITY,
                        Severity.P3,
                        line.filePath(),
                        line.lineNumber(),
                        "新增标准输出调试语句",
                        "System.out.println 绕过日志级别、TraceId 和集中采集，容易遗留到生产环境",
                        "改用项目统一 Logger，并保留必要上下文字段",
                        AgentSupport.shortEvidence(content)
                ));
            }

            if (content.contains("Map<String, Object>")) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.QUALITY,
                        Severity.P3,
                        line.filePath(),
                        line.lineNumber(),
                        "使用弱类型 Map 承载业务数据",
                        "Map<String, Object> 会让字段契约散落在调用链里，重构和测试都更脆弱",
                        "定义明确的 DTO 或领域对象，并用校验注解表达字段约束",
                        AgentSupport.shortEvidence(content)
                ));
            }
        }

        for (ChangedFile file : context.parsedDiff().files()) {
            if (!file.isJavaProductionFile()) {
                continue;
            }

            if (file.additions() >= 80) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.QUALITY,
                        Severity.P2,
                        file.displayPath(),
                        null,
                        "单文件新增代码量较大",
                        "一个文件新增 " + file.additions() + " 行，可能混入多个职责",
                        "考虑拆分 Controller、Service、策略类或测试辅助对象",
                        "additions=" + file.additions()
                ));
            }

            boolean isController = file.displayPath().endsWith("Controller.java")
                    || AgentSupport.fileContains(file, "@RestController");

            boolean touchesPersistence = AgentSupport.fileContains(file, "Repository")
                    || AgentSupport.fileContains(file, "Mapper")
                    || AgentSupport.fileContains(file, "JdbcTemplate");

            if (isController && touchesPersistence) {
                findings.add(AgentSupport.finding(
                        type(),
                        IssueTag.QUALITY,
                        Severity.P2,
                        file.displayPath(),
                        null,
                        "Controller 直接访问持久层",
                        "Controller 中出现 Repository、Mapper 或 JdbcTemplate，容易把接口层和业务层混在一起",
                        "把业务编排放入 Service，Controller 只负责参数接收和响应返回",
                        "path=" + file.displayPath()
                ));
            }
        }

        return new AgentExecutionResult(
                type(),
                findings,
                "CodeQualityAgent 发现 " + findings.size() + " 个质量问题"
        );
    }
}