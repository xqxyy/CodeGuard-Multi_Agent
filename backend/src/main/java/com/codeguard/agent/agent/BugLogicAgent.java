package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ChangedFile;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 缺陷逻辑智能体
 *
 * 负责检查空值返回、直接取值、宽泛异常、调试异常输出和事务边界等常见 Java 后端缺陷
 */
@Component
public class BugLogicAgent implements ReviewAgent {

  /** 匹配 return null 语句 */
  private static final Pattern RETURN_NULL = Pattern.compile("\\breturn\\s+null\\s*;");

  /** 匹配 Optional 或查询结果直接 get 的写法 */
  private static final Pattern DIRECT_GET = Pattern.compile("\\.get\\s*\\(\\s*\\)");

  /** 匹配 catch Exception 或 catch Throwable */
  private static final Pattern CATCH_WIDE_EXCEPTION =
      Pattern.compile("\\bcatch\\s*\\(\\s*(Exception|Throwable)\\b");

  /**
   * 返回当前 Agent 类型
   */
  @Override
  public AgentType type() {
    return AgentType.BUG_LOGIC;
  }

  /**
   * 执行缺陷逻辑审查
   */
  @Override
  public AgentExecutionResult review(ReviewContext context) {
    List<ReviewFinding> findings = new ArrayList<>();

    for (AgentSupport.LineRef line : AgentSupport.addedLines(context.parsedDiff())) {
      String content = line.content();

      if (AgentSupport.matches(content, RETURN_NULL)) {
        findings.add(
            AgentSupport.finding(
                type(),
                IssueTag.BUG,
                Severity.P2,
                line.filePath(),
                line.lineNumber(),
                "新增 return null 可能导致空指针风险",
                "新增代码直接返回 null，调用方如果没有判空，可能触发 NullPointerException",
                "优先返回空集合、Optional，或抛出明确的业务异常",
                AgentSupport.shortEvidence(content)));
      }

      if (AgentSupport.matches(content, DIRECT_GET)) {
        findings.add(
            AgentSupport.finding(
                type(),
                IssueTag.BUG,
                Severity.P2,
                line.filePath(),
                line.lineNumber(),
                "直接调用 get 缺少空结果保护",
                "新增代码直接对 Optional、集合或查询结果调用 get，空结果时可能抛出运行时异常",
                "使用 orElseThrow、orElse、显式判空，或补充空结果分支",
                AgentSupport.shortEvidence(content)));
      }

      if (AgentSupport.matches(content, CATCH_WIDE_EXCEPTION)) {
        findings.add(
            AgentSupport.finding(
                type(),
                IssueTag.BUG,
                Severity.P1,
                line.filePath(),
                line.lineNumber(),
                "捕获宽泛异常可能吞掉真实失败",
                "catch Exception 或 catch Throwable 会把业务异常和系统异常混在一起，容易破坏事务、告警和调用方语义",
                "缩小异常类型，保留上下文；需要失败时继续抛出业务异常或系统异常",
                AgentSupport.shortEvidence(content)));
      }

      if (content.contains("printStackTrace()")) {
        findings.add(
            AgentSupport.finding(
                type(),
                IssueTag.BUG,
                Severity.P2,
                line.filePath(),
                line.lineNumber(),
                "使用 printStackTrace 会绕过统一日志",
                "异常只打印到标准输出时，生产环境通常无法关联 TraceId、请求参数和告警规则",
                "使用项目统一 Logger 记录异常对象，并根据业务语义决定是否继续抛出",
                AgentSupport.shortEvidence(content)));
      }
    }

    for (ChangedFile file : context.parsedDiff().files()) {
      boolean serviceFile =
          file.displayPath().endsWith("Service.java") || AgentSupport.pathContains(file, "/service/");
      boolean writesData =
          AgentSupport.fileContains(file, ".save(")
              || AgentSupport.fileContains(file, ".delete(")
              || AgentSupport.fileContains(file, ".update(");
      boolean hasTransaction = AgentSupport.fileContains(file, "@Transactional");

      if (file.isJavaProductionFile() && serviceFile && writesData && !hasTransaction) {
        findings.add(
            AgentSupport.finding(
                type(),
                IssueTag.BUG,
                Severity.P2,
                file.displayPath(),
                null,
                "Service 写操作缺少事务边界信号",
                "本次变更包含保存、删除或更新操作，但文件上下文里没有看到 @Transactional",
                "确认上游已有事务；如果没有，在 Service 方法或类上声明事务并补充回滚测试",
                "path=" + file.displayPath()));
      }
    }

    return new AgentExecutionResult(type(), findings, "BugLogicAgent 发现 " + findings.size() + " 个问题");
  }
}
