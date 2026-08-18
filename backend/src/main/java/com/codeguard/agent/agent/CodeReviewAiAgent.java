package com.codeguard.agent.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * LangChain4j 的 LLM Agent 接口。
 *
 * 这个接口不需要手写实现类，LangChain4j 会根据注解创建代理对象。
 */
public interface CodeReviewAiAgent {

    /**
     * 调用大模型执行二次代码审查。
     *
     * 这里强制模型只返回 JSON，是为了让后端可以稳定解析，而不是依赖自然语言。
     */
    @Agent(
            name = "llmReviewAgent",
            outputKey = "llmReviewJson",
            description = "Use LLM to re-check Java backend diff risks after rule agents finished"
    )
    @UserMessage(
            """
            你是 CodeGuard-Agent 的 LLM 代码审查智能体，负责审查 Java/Spring 后端项目的 Git diff。

            你的任务：
            1. 阅读 diff 摘要。
            2. 阅读规则 Agent 已经发现的问题。
            3. 阅读关键变更行。
            4. 只补充规则 Agent 可能漏掉的高价值问题。

            输出要求：
            - 必须只输出合法 JSON。
            - 不要输出 Markdown。
            - 不要输出解释性自然语言。
            - findings 最多 5 条。
            - 不要重复规则 Agent 已经发现的问题。

            JSON Schema：
            {
              "summary": "中文简短总结",
              "findings": [
                {
                  "tag": "BUG|SECURITY|QUALITY|TEST_GAP",
                  "severity": "P0|P1|P2|P3",
                  "filePath": "文件路径或 null",
                  "lineNumber": 12,
                  "title": "中文问题标题",
                  "detail": "中文问题说明",
                  "suggestion": "中文修复建议",
                  "evidence": "简短代码证据"
                }
              ]
            }

            重点关注：
            - 鉴权、越权、权限注解删除。
            - SQL 注入、命令注入、路径穿越、文件上传。
            - 事务边界、并发一致性、异常吞掉、空指针。
            - 新增生产代码但缺少测试覆盖。
            - 敏感信息、Token、密钥硬编码。

            如果没有额外高置信问题，返回：
            {"summary":"未发现额外高置信问题","findings":[]}

            diff 摘要：
            {{diffSummary}}

            规则 Agent 发现的问题 JSON：
            {{ruleFindingsJson}}

            关键变更行：
            {{diffSnippet}}
            """
    )
    String review(
            @V("diffSummary") String diffSummary,
            @V("ruleFindingsJson") String ruleFindingsJson,
            @V("diffSnippet") String diffSnippet
    );
}
