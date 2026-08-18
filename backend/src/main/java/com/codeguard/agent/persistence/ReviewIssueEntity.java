package com.codeguard.agent.persistence;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.Severity;
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
 * Review 问题实体
 *
 * 这个类映射 review_issues 表
 * 一条 Review 可以对应多条问题
 */
@Entity
@Table(name = "review_issues")
public class ReviewIssueEntity {

    /** 问题编号 */
    @Id
    private UUID id;

    /** 所属 Review */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private ReviewEntity review;

    /** 发现该问题的 Agent */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AgentType agentType;

    /** 问题标签 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IssueTag tag;

    /** 严重级别 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity;

    /** 文件路径 */
    @Column(length = 600)
    private String filePath;

    /** 行号 */
    private Integer lineNumber;

    /** 问题标题 */
    @Column(nullable = false, length = 240)
    private String title;

    /** 问题详情 */
    @Lob
    @Column(nullable = false)
    private String detail;

    /** 修复建议 */
    @Lob
    @Column(nullable = false)
    private String suggestion;

    /** 代码证据 */
    @Lob
    private String evidence;

    /** 创建时间 */
    @Column(nullable = false)
    private Instant createdAt;

    protected ReviewIssueEntity() {}

    /**
     * 把 Agent 输出的 ReviewFinding 转成数据库实体
     */
    public static ReviewIssueEntity from(ReviewEntity review, ReviewFinding finding) {
        ReviewIssueEntity entity = new ReviewIssueEntity();
        entity.id = UUID.randomUUID();
        entity.review = review;
        entity.agentType = finding.agentType();
        entity.tag = finding.tag();
        entity.severity = finding.severity();
        entity.filePath = finding.filePath();
        entity.lineNumber = finding.lineNumber();
        entity.title = finding.title();
        entity.detail = finding.detail();
        entity.suggestion = finding.suggestion();
        entity.evidence = finding.evidence();
        entity.createdAt = Instant.now();

        return entity;
    }

    public UUID getId() {
        return id;
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public IssueTag getTag() {
        return tag;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getEvidence() {
        return evidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}