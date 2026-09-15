package com.codeguard.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codeguard.agent.config.CodeGuardChatModelProvider;
import com.codeguard.agent.diff.GitDiffParser;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewContextSnapshot;
import com.codeguard.agent.domain.ReviewToolObservation;
import com.codeguard.agent.service.GithubRepositoryContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LlmReviewAgentToolCallingTest {

    @Test
    void completesReviewAfterBoundedReadOnlyToolCall() {
        CodeGuardChatModelProvider provider = mock(CodeGuardChatModelProvider.class);
        ChatModel chatModel = mock(ChatModel.class);
        GithubRepositoryContextService githubTools = mock(GithubRepositoryContextService.class);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("call-1")
                .name("github_read_changed_file")
                .arguments("{\"path\":\"src/main/java/demo/AccountService.java\"}")
                .build();

        when(provider.chatModel()).thenReturn(Optional.of(chatModel));
        when(provider.modelName()).thenReturn("test-model");
        when(provider.providerName()).thenReturn("test-provider");
        when(githubTools.availableTools(any())).thenReturn(List.of(ToolSpecification.builder()
                .name("github_read_changed_file")
                .description("test")
                .build()));
        when(githubTools.execute(any(), any())).thenReturn(new ReviewToolObservation(
                "github_read_changed_file", "GitHub 变更文件上下文：src/main/java/demo/AccountService.java", "fixed", "class AccountService {}"
        ));
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from(request)).build())
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("""
                        {"summary":"发现空指针风险","findings":[{"tag":"BUG","severity":"P1","filePath":"src/main/java/demo/AccountService.java","lineNumber":12,"title":"可能空指针","detail":"返回值未校验","suggestion":"增加非空判断","evidence":"repository.findById"}]}
                        """)).build());

        ReviewContext context = new ReviewContext(
                "tool-call",
                "diff --git a/src/main/java/demo/AccountService.java b/src/main/java/demo/AccountService.java\n"
                        + "+++ b/src/main/java/demo/AccountService.java\n@@ -1 +1 @@\n+class AccountService {}\n",
                new GitDiffParser().parse("diff --git a/src/main/java/demo/AccountService.java b/src/main/java/demo/AccountService.java\n"
                        + "+++ b/src/main/java/demo/AccountService.java\n@@ -1 +1 @@\n+class AccountService {}\n"),
                new RouterAgent().route(new GitDiffParser().parse(""), ""),
                ReviewContextSnapshot.empty()
        );

        LlmReviewAgent.LlmReviewRun result = new LlmReviewAgent(provider, new ObjectMapper(), githubTools)
                .review(context, List.of());

        assertThat(result.findings()).hasSize(1);
        assertThat(result.trace().outputSummary()).contains("工具调用 1 次");
        assertThat(result.trace().rawOutput()).contains("github_read_changed_file");
    }
}
