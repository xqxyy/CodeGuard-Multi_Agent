package com.codeguard.agent.service;

import com.codeguard.agent.api.ReviewOptions;
import com.codeguard.agent.api.ReviewPolicyDto;
import com.codeguard.agent.api.ReviewPolicyRequest;
import com.codeguard.agent.persistence.ReviewPolicyEntity;
import com.codeguard.agent.persistence.ReviewPolicyRepository;
import com.codeguard.agent.security.CurrentUserService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审查策略服务。
 *
 * 策略是企业项目里“平台约束”的入口，例如强制开启安全 Agent、
 * 限制超大 diff、或者规定 P0 风险必须阻断合并。
 */
@Service
public class PolicyService {

    private final ReviewPolicyRepository policyRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public PolicyService(
            ReviewPolicyRepository policyRepository,
            CurrentUserService currentUserService,
            AuditLogService auditLogService
    ) {
        this.policyRepository = policyRepository;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public ReviewPolicyEntity ensurePolicy(String projectKey) {
        String organizationKey = currentUserService.organizationKey();
        String normalizedProjectKey = normalizeProjectKey(projectKey);

        return policyRepository.findByOrganizationKeyAndProjectKey(organizationKey, normalizedProjectKey)
                .orElseGet(() -> policyRepository.save(ReviewPolicyEntity.defaults(
                        organizationKey,
                        normalizedProjectKey
                )));
    }

    @Transactional(readOnly = true)
    public List<ReviewPolicyDto> listPolicies() {
        return policyRepository.findByOrganizationKeyOrderByUpdatedAtDesc(currentUserService.organizationKey())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ReviewPolicyDto getPolicy(String projectKey) {
        return toDto(ensurePolicy(projectKey));
    }

    @Transactional
    public ReviewPolicyDto updatePolicy(String projectKey, ReviewPolicyRequest request) {
        ReviewPolicyEntity policy = ensurePolicy(projectKey);
        policy.update(request);
        ReviewPolicyEntity saved = policyRepository.save(policy);
        auditLogService.record("POLICY_UPDATED", "POLICY", saved.getProjectKey(),
                "更新项目审查策略 " + saved.getName());
        return toDto(saved);
    }

    @Transactional
    public ReviewOptions resolveOptions(String projectKey, ReviewOptions requestOptions) {
        ReviewPolicyEntity policy = ensurePolicy(projectKey);
        if (requestOptions == null) {
            return policy.toReviewOptions();
        }

        return new ReviewOptions(
                requestOptions.enableBugLogic() == null ? policy.isEnableBugLogic() : requestOptions.enableBugLogic(),
                // 项目策略是组织级约束，单次请求不能把已启用的安全审查关闭。
                policy.isEnableSecurity() || Boolean.TRUE.equals(requestOptions.enableSecurity()),
                requestOptions.enableCodeQuality() == null ? policy.isEnableCodeQuality() : requestOptions.enableCodeQuality(),
                requestOptions.enableTestCoverage() == null ? policy.isEnableTestCoverage() : requestOptions.enableTestCoverage(),
                requestOptions.enableLlmReview() == null ? policy.isEnableLlmReview() : requestOptions.enableLlmReview(),
                policy.isFailOnP0() || Boolean.TRUE.equals(requestOptions.failOnP0())
        );
    }

    @Transactional
    public void validateDiffSize(String projectKey, String diffText) {
        ReviewPolicyEntity policy = ensurePolicy(projectKey);
        int size = diffText == null ? 0 : diffText.length();
        if (size > policy.getMaxDiffChars()) {
            throw new IllegalArgumentException(
                    "Git diff is too large for current policy. size="
                            + size
                            + ", maxDiffChars="
                            + policy.getMaxDiffChars()
            );
        }
    }

    private ReviewPolicyDto toDto(ReviewPolicyEntity entity) {
        return new ReviewPolicyDto(
                entity.getId(),
                entity.getOrganizationKey(),
                entity.getProjectKey(),
                entity.getName(),
                entity.getBlockSeverity(),
                entity.isFailOnP0(),
                entity.isRequireTestsForApiChanges(),
                entity.isEnableBugLogic(),
                entity.isEnableSecurity(),
                entity.isEnableCodeQuality(),
                entity.isEnableTestCoverage(),
                entity.isEnableLlmReview(),
                entity.getMaxDiffChars(),
                entity.getUpdatedAt()
        );
    }

    private String normalizeProjectKey(String projectKey) {
        return projectKey == null || projectKey.isBlank() ? "default" : projectKey.strip();
    }
}
