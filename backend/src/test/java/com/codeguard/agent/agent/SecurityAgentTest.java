package com.codeguard.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeguard.agent.diff.GitDiffParser;
import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.RouterDecision;
import com.codeguard.agent.domain.Severity;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * SecurityAgent 测试
 *
 * 重点验证安全规则能识别 SQL 拼接和硬编码密钥
 */
class SecurityAgentTest {

  private final GitDiffParser parser = new GitDiffParser();
  private final SecurityAgent securityAgent = new SecurityAgent();

  /**
   * 新增 SQL 字符串拼接时，应生成 P0 安全问题
   */
  @Test
  void detectsSqlConcatenation() {
    String diff =
        """
        diff --git a/src/main/java/UserRepository.java b/src/main/java/UserRepository.java
        --- a/src/main/java/UserRepository.java
        +++ b/src/main/java/UserRepository.java
        @@ -1,3 +1,4 @@
         public void search(String keyword) {
        +  String sql = "select * from users where name = '" + keyword + "'";
         }
        """;

    AgentExecutionResult result = securityAgent.review(context(diff));

    assertThat(result.findings())
        .anySatisfy(
            finding -> {
              assertThat(finding.tag()).isEqualTo(IssueTag.SECURITY);
              assertThat(finding.severity()).isEqualTo(Severity.P0);
              assertThat(finding.title()).contains("SQL");
            });
  }

  /**
   * 新增配置密钥时，应生成硬编码凭据问题
   */
  @Test
  void detectsHardcodedSecret() {
    String diff =
        """
        diff --git a/src/main/resources/application.yml b/src/main/resources/application.yml
        --- a/src/main/resources/application.yml
        +++ b/src/main/resources/application.yml
        @@ -1,1 +1,2 @@
         payment: true
        +api-key: sk_live_abcdef123456
        """;

    AgentExecutionResult result = securityAgent.review(context(diff));

    assertThat(result.findings())
        .anySatisfy(finding -> assertThat(finding.title()).contains("硬编码"));
  }

  /**
   * 构造 Agent 审查所需上下文
   */
  private ReviewContext context(String diff) {
    return new ReviewContext(
        "security-test",
        diff,
        parser.parse(diff),
        new RouterDecision(Set.of(AgentType.SECURITY), Map.of(), Map.of()));
  }
}
