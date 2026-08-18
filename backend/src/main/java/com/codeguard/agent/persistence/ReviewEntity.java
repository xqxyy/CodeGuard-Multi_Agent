package com.codeguard.agent.persistence;

import com.codeguard.agent.api.ReviewOptions;
import com.codeguard.agent.api.ReviewRequest;
import com.codeguard.agent.domain.MergeRecommendation;
import com.codeguard.agent.domain.ParsedDiff;
import com.codeguard.agent.domain.ReviewSourceType;
import com.codeguard.agent.domain.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Review 主记录实体。
 *
 * 一次代码审查会生成一条 Review 主记录，问题和 Agent Trace 都挂在这条记录下面。
 */
@Entity
@Table(name = "reviews")
public class ReviewEntity {

    /** Review 唯一编号，也是异步任务编号。 */
    @Id
    private UUID id;

    /** 所属项目。企业场景里同一个平台会服务多个项目。 */
    @Column(nullable = false, length = 80)
    private String projectKey;

    /** 仓库名或人工输入来源名。 */
    @Column(nullable = false, length = 180)
    private String repositoryName;

    /** 本次审查来源：手动、样例、GitHub PR。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReviewSourceType sourceType;

    /** 来源链接，例如 GitHub PR URL。 */
    @Column(length = 600)
    private String sourceUrl;

    /** Review 标题。 */
    @Column(nullable = false, length = 200)
    private String title;

    /** Review 状态。异步任务会经历 QUEUED -> RUNNING -> COMPLETED/FAILED。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReviewStatus status;

    /** 原始 Git diff 文本。 */
    @Lob
    @Column(nullable = false)
    private String diffText;

    /** Markdown 汇总报告。 */
    @Lob
    private String markdown;

    /** 失败时保存错误信息，前端可以直接展示。 */
    @Lob
    private String errorMessage;

    /** 合并建议。 */
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MergeRecommendation recommendation;

    /** 风险分。 */
    @Column(nullable = false)
    private int riskScore;

    /** 变更文件数。 */
    @Column(nullable = false)
    private int filesChanged;

    /** 新增行数。 */
    @Column(nullable = false)
    private int additions;

    /** 删除行数。 */
    @Column(nullable = false)
    private int deletions;

    /** Agent 开关：让演示时可以按场景打开或关闭不同 Agent。 */
    @Column(nullable = false)
    private boolean enableBugLogic;

    @Column(nullable = false)
    private boolean enableSecurity;

    @Column(nullable = false)
    private boolean enableCodeQuality;

    @Column(nullable = false)
    private boolean enableTestCoverage;

    @Column(nullable = false)
    private boolean enableLlmReview;

    /** 是否把 P0 风险视为阻断合并。 */
    @Column(nullable = false)
    private boolean failOnP0;

    /** 创建时间。 */
    @Column(nullable = false)
    private Instant createdAt;

    /** 更新时间。 */
    @Column(nullable = false)
    private Instant updatedAt;

    protected ReviewEntity() {}

    /**
     * 创建一条异步 Review 任务。
     */
    public static ReviewEntity createQueued(ReviewRequest request, ParsedDiff parsedDiff) {
        Instant now = Instant.now();
        ReviewOptions options = request.normalizedOptions();

        ReviewEntity entity = new ReviewEntity();
        entity.id = UUID.randomUUID();
        entity.projectKey = request.normalizedProjectKey();
        entity.repositoryName = request.normalizedRepositoryName();
        entity.sourceType = parseSourceType(request.normalizedSourceType());
        entity.sourceUrl = blankToNull(request.sourceUrl());
        entity.title = request.title() == null || request.title().isBlank()
                ? "Untitled review"
                : request.title().strip();
        entity.status = ReviewStatus.QUEUED;
        entity.diffText = request.diffText();
        entity.markdown = "";
        entity.errorMessage = null;
        entity.recommendation = MergeRecommendation.APPROVE;
        entity.riskScore = 0;
        entity.filesChanged = parsedDiff.summary().filesChanged();
        entity.additions = parsedDiff.summary().additions();
        entity.deletions = parsedDiff.summary().deletions();
        entity.enableBugLogic = Boolean.TRUE.equals(options.enableBugLogic());
        entity.enableSecurity = Boolean.TRUE.equals(options.enableSecurity());
        entity.enableCodeQuality = Boolean.TRUE.equals(options.enableCodeQuality());
        entity.enableTestCoverage = Boolean.TRUE.equals(options.enableTestCoverage());
        entity.enableLlmReview = Boolean.TRUE.equals(options.enableLlmReview());
        entity.failOnP0 = Boolean.TRUE.equals(options.failOnP0());
        entity.createdAt = now;
        entity.updatedAt = now;

        return entity;
    }

    /**
     * 兼容旧同步代码路径。
     */
    public static ReviewEntity create(String title, String diffText, ParsedDiff parsedDiff) {
        return createQueued(new ReviewRequest(title, diffText), parsedDiff);
    }

    public void start() {
        this.status = ReviewStatus.RUNNING;
        this.updatedAt = Instant.now();
    }

    public void complete(String markdown, MergeRecommendation recommendation, int riskScore) {
        this.status = ReviewStatus.COMPLETED;
        this.markdown = markdown;
        this.recommendation = recommendation;
        this.riskScore = riskScore;
        this.errorMessage = null;
        this.updatedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = ReviewStatus.FAILED;
        this.errorMessage = message;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        this.status = ReviewStatus.CANCELED;
        this.updatedAt = Instant.now();
    }

    private static ReviewSourceType parseSourceType(String value) {
        try {
            return ReviewSourceType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ReviewSourceType.MANUAL;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() {
        return id;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public ReviewSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getTitle() {
        return title;
    }

    public ReviewStatus getStatus() {
        return status;
    }

    public String getDiffText() {
        return diffText;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public MergeRecommendation getRecommendation() {
        return recommendation;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public int getFilesChanged() {
        return filesChanged;
    }

    public int getAdditions() {
        return additions;
    }

    public int getDeletions() {
        return deletions;
    }

    public boolean isEnableBugLogic() {
        return enableBugLogic;
    }

    public boolean isEnableSecurity() {
        return enableSecurity;
    }

    public boolean isEnableCodeQuality() {
        return enableCodeQuality;
    }

    public boolean isEnableTestCoverage() {
        return enableTestCoverage;
    }

    public boolean isEnableLlmReview() {
        return enableLlmReview;
    }

    public boolean isFailOnP0() {
        return failOnP0;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
