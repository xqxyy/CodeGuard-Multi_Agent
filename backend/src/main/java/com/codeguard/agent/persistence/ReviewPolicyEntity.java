package com.codeguard.agent.persistence;

import com.codeguard.agent.api.ReviewOptions;
import com.codeguard.agent.api.ReviewPolicyRequest;
import com.codeguard.agent.domain.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Review 策略实体。
 *
 * 策略决定一次审查默认启用哪些 Agent，以及哪些风险会阻断合并。
 */
@Entity
@Table(name = "review_policies")
public class ReviewPolicyEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String organizationKey;

    @Column(nullable = false, length = 80)
    private String projectKey;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity blockSeverity;

    @Column(name = "fail_on_p0", nullable = false)
    private boolean failOnP0;

    @Column(nullable = false)
    private boolean requireTestsForApiChanges;

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

    @Column(nullable = false)
    private int maxDiffChars;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ReviewPolicyEntity() {}

    public static ReviewPolicyEntity defaults(String organizationKey, String projectKey) {
        Instant now = Instant.now();

        ReviewPolicyEntity entity = new ReviewPolicyEntity();
        entity.id = UUID.randomUUID();
        entity.organizationKey = organizationKey;
        entity.projectKey = projectKey;
        entity.name = "企业默认审查策略";
        entity.blockSeverity = Severity.P0;
        entity.failOnP0 = true;
        entity.requireTestsForApiChanges = true;
        entity.enableBugLogic = true;
        entity.enableSecurity = true;
        entity.enableCodeQuality = true;
        entity.enableTestCoverage = true;
        entity.enableLlmReview = true;
        entity.maxDiffChars = 200000;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(ReviewPolicyRequest request) {
        if (request.name() != null && !request.name().isBlank()) {
            this.name = request.name().strip();
        }
        this.blockSeverity = request.blockSeverity() == null ? this.blockSeverity : request.blockSeverity();
        this.failOnP0 = valueOrCurrent(request.failOnP0(), this.failOnP0);
        this.requireTestsForApiChanges = valueOrCurrent(
                request.requireTestsForApiChanges(),
                this.requireTestsForApiChanges
        );
        this.enableBugLogic = valueOrCurrent(request.enableBugLogic(), this.enableBugLogic);
        this.enableSecurity = valueOrCurrent(request.enableSecurity(), this.enableSecurity);
        this.enableCodeQuality = valueOrCurrent(request.enableCodeQuality(), this.enableCodeQuality);
        this.enableTestCoverage = valueOrCurrent(request.enableTestCoverage(), this.enableTestCoverage);
        this.enableLlmReview = valueOrCurrent(request.enableLlmReview(), this.enableLlmReview);
        this.maxDiffChars = request.maxDiffChars() == null ? this.maxDiffChars : Math.max(1000, request.maxDiffChars());
        this.updatedAt = Instant.now();
    }

    public ReviewOptions toReviewOptions() {
        return new ReviewOptions(
                enableBugLogic,
                enableSecurity,
                enableCodeQuality,
                enableTestCoverage,
                enableLlmReview,
                failOnP0
        );
    }

    private boolean valueOrCurrent(Boolean value, boolean current) {
        return value == null ? current : value;
    }

    public UUID getId() {
        return id;
    }

    public String getOrganizationKey() {
        return organizationKey;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getName() {
        return name;
    }

    public Severity getBlockSeverity() {
        return blockSeverity;
    }

    public boolean isFailOnP0() {
        return failOnP0;
    }

    public boolean isRequireTestsForApiChanges() {
        return requireTestsForApiChanges;
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

    public int getMaxDiffChars() {
        return maxDiffChars;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
