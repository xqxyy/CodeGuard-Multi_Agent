package com.codeguard.agent.persistence;

import com.codeguard.agent.domain.AgentStatus;
import com.codeguard.agent.domain.AgentTraceRecord;
import com.codeguard.agent.domain.AgentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Agent 执行轨迹实体
 *
 * 这个类映射 agent_traces 表
 * 用于保存每个 Agent 的运行状态、输入摘要、输出摘要、耗时和跳过原因
 */
@Entity
@Table(name = "agent_traces")
public class AgentTraceEntity {

    /** Trace 编号 */
    @Id
    private UUID id;

    /** 所属 Review */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private ReviewEntity review;

    /** Agent 类型 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AgentType agentType;

    /** Agent 状态 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AgentStatus status;

    /** 输入摘要 */
    @Lob
    @Column(nullable = false)
    private String inputSummary;

    /** 输出摘要 */
    @Lob
    private String outputSummary;

    /** 跳过原因 */
    @Lob
    private String skipReason;

    /** 耗时毫秒 */
    @Column(nullable = false)
    private long durationMs;

    /** 开始时间 */
    @Column(nullable = false)
    private Instant startedAt;

    /** 结束时间 */
    @Column(nullable = false)
    private Instant endedAt;

    /** LLM Agent 使用的提示词，规则 Agent 留空。 */
    @Lob
    private String prompt;

    /** LLM 原始输出，方便审计和排查解析失败。 */
    @Lob
    private String rawOutput;

    /** 模型名称。 */
    @Column(length = 120)
    private String modelName;

    /** 模型供应商。 */
    @Column(length = 80)
    private String provider;

    /** 输入 token 数，当前部分供应商可能拿不到，允许为空。 */
    private Integer promptTokens;

    /** 输出 token 数，当前部分供应商可能拿不到，允许为空。 */
    private Integer completionTokens;

    /** Agent 失败时的错误信息。 */
    @Lob
    private String errorMessage;

    protected AgentTraceEntity() {}

    /**
     * 把内存中的 AgentTraceRecord 转成数据库实体
     */
    public static AgentTraceEntity from(ReviewEntity review, AgentTraceRecord trace) {
        AgentTraceEntity entity = new AgentTraceEntity();
        entity.id = UUID.randomUUID();
        entity.review = review;
        entity.agentType = trace.agentType();
        entity.status = trace.status();
        entity.inputSummary = trace.inputSummary();
        entity.outputSummary = trace.outputSummary();
        entity.skipReason = trace.skipReason();
        entity.durationMs = trace.durationMs();
        entity.startedAt = trace.startedAt();
        entity.endedAt = trace.endedAt();
        entity.prompt = trace.prompt();
        entity.rawOutput = trace.rawOutput();
        entity.modelName = trace.modelName();
        entity.provider = trace.provider();
        entity.promptTokens = trace.promptTokens();
        entity.completionTokens = trace.completionTokens();
        entity.errorMessage = trace.errorMessage();

        return entity;
    }

    public UUID getId() {
        return id;
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public String getOutputSummary() {
        return outputSummary;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getRawOutput() {
        return rawOutput;
    }

    public String getModelName() {
        return modelName;
    }

    public String getProvider() {
        return provider;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
