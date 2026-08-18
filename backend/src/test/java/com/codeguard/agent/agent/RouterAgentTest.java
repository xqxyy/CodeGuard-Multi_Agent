package com.codeguard.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeguard.agent.diff.GitDiffParser;
import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.RouterDecision;
import org.junit.jupiter.api.Test;

/**
 * RouterAgent 测试
 *
 * 重点验证 Router 能根据文件类型和内容选择或跳过不同 Agent
 */
class RouterAgentTest {

  private final GitDiffParser parser = new GitDiffParser();
  private final RouterAgent routerAgent = new RouterAgent();

  /**
   * 只修改 Markdown 文档时，应跳过所有代码审查 Agent
   */
  @Test
  void skipsAgentsForMarkdownOnlyDiff() {
    String diff =
        """
        diff --git a/README.md b/README.md
        --- a/README.md
        +++ b/README.md
        @@ -1,1 +1,1 @@
        -old
        +new
        """;

    RouterDecision decision = routerAgent.route(parser.parse(diff), diff);

    assertThat(decision.enabledAgents()).isEmpty();
    assertThat(decision.reasonFor(AgentType.SECURITY)).contains("只修改文档");
  }

  /**
   * SQL 相关变更应触发安全和测试覆盖 Agent
   */
  @Test
  void enablesSecurityAndTestCoverageForSqlDiff() {
    String diff =
        """
        diff --git a/src/main/resources/user.sql b/src/main/resources/user.sql
        --- a/src/main/resources/user.sql
        +++ b/src/main/resources/user.sql
        @@ -1,1 +1,1 @@
        -select * from users;
        +select * from users where name = ${name};
        """;

    RouterDecision decision = routerAgent.route(parser.parse(diff), diff);

    assertThat(decision.shouldRun(AgentType.SECURITY)).isTrue();
    assertThat(decision.shouldRun(AgentType.TEST_COVERAGE)).isTrue();
  }
}
