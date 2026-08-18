package com.codeguard.agent.api;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeguard.agent.agent.LlmReviewAgent;
import com.codeguard.agent.domain.AgentStatus;
import com.codeguard.agent.domain.AgentTraceRecord;
import com.codeguard.agent.domain.AgentType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ReviewController 测试
 *
 * 重点验证 HTTP 接口能返回样例、执行样例 Review，并返回统一错误结构
 */
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:controller-test;MODE=PostgreSQL;DATABASE_TO_UPPER=false",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
@AutoConfigureMockMvc(addFilters = false)
class ReviewControllerTest {

  @Autowired private MockMvc mockMvc;

  /** 测试里 mock LLM，避免真实请求模型接口 */
  @MockBean private LlmReviewAgent llmReviewAgent;

  /**
   * 每个测试前固定 LLM 返回结果
   */
  @BeforeEach
  void setUp() {
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
   * 验证样例列表接口可用
   */
  @Test
  void listsSamples() throws Exception {
    mockMvc
        .perform(get("/api/samples"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()", greaterThanOrEqualTo(7)))
        .andExpect(jsonPath("$[0].id").exists());
  }

  /**
   * 验证样例 Review 接口可用，并能返回问题列表和 Trace
   */
  @Test
  void reviewsSampleDiff() throws Exception {
    mockMvc
        .perform(post("/api/samples/return-null/reviews/sync"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.issues.length()", greaterThanOrEqualTo(1)))
        .andExpect(jsonPath("$.traces.length()", greaterThanOrEqualTo(1)));
  }

  /**
   * 验证不存在的样例会返回统一错误结构
   */
  @Test
  void returnsStructuredErrorForMissingSample() throws Exception {
    mockMvc
        .perform(post("/api/samples/not-exist/reviews"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value("Sample diff not found: not-exist"));
  }
}
