package com.codeguard.agent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.codeguard.agent.agent.LlmReviewAgent;
import com.codeguard.agent.api.ReviewRequest;
import com.codeguard.agent.api.ReviewResponse;
import com.codeguard.agent.domain.AgentStatus;
import com.codeguard.agent.domain.AgentTraceRecord;
import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.persistence.AgentTraceRepository;
import com.codeguard.agent.persistence.ReviewIssueRepository;
import com.codeguard.agent.persistence.ReviewRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * ReviewWorkflowService 集成测试
 *
 * 重点验证完整工作流会执行 Agent、保存数据库，并能查询历史详情
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:workflow-test;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
class ReviewWorkflowServiceTest {

  @Autowired private ReviewWorkflowService reviewWorkflowService;
  @Autowired private ReviewRepository reviewRepository;
  @Autowired private ReviewIssueRepository issueRepository;
  @Autowired private AgentTraceRepository traceRepository;

  /** 测试里 mock LLM，避免真实调用外部模型接口 */
  @MockBean private LlmReviewAgent llmReviewAgent;

  /**
   * 每个测试前清空数据库，并让 LLM 固定返回跳过 Trace
   */
  @BeforeEach
  void setUp() {
    traceRepository.deleteAll();
    issueRepository.deleteAll();
    reviewRepository.deleteAll();

    Instant now = Instant.now();
    AgentTraceRecord trace =
        new AgentTraceRecord(
            AgentType.LLM_REVIEW,
            AgentStatus.SKIPPED,
            "test",
            null,
            "测试环境不调用 LLM",
            0,
            now,
            now);

    when(llmReviewAgent.review(any(), anyList()))
        .thenReturn(new LlmReviewAgent.LlmReviewRun(List.of(), trace));
  }

  /**
   * 验证 return null 样例能生成问题、Trace 和历史记录
   */
  @Test
  void reviewsDiffAndPersistsResult() {
    ReviewResponse response =
        reviewWorkflowService.review(
            new ReviewRequest(
                "return-null",
                """
                diff --git a/src/main/java/UserService.java b/src/main/java/UserService.java
                --- a/src/main/java/UserService.java
                +++ b/src/main/java/UserService.java
                @@ -1,3 +1,3 @@
                 public User find(Long id) {
                -  return repository.findById(id).orElseThrow();
                +  return null;
                 }
                """));

    assertThat(response.id()).isNotNull();
    assertThat(response.issues())
        .anySatisfy(issue -> assertThat(issue.tag()).isEqualTo(IssueTag.BUG));
    assertThat(response.traces())
        .extracting(trace -> trace.agentType())
        .contains(AgentType.BUG_LOGIC, AgentType.LLM_REVIEW);
    assertThat(reviewWorkflowService.listRecent()).hasSize(1);
    assertThat(reviewWorkflowService.get(response.id()).issues()).hasSize(response.issues().size());
    assertThat(reviewWorkflowService.markdown(response.id())).contains("CodeGuard Review Report");
  }
}
