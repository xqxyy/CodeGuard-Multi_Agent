package com.codeguard.agent.service;

import com.codeguard.agent.api.DashboardSummaryDto;
import com.codeguard.agent.api.ReviewListItem;
import com.codeguard.agent.domain.MergeRecommendation;
import com.codeguard.agent.domain.ReviewStatus;
import com.codeguard.agent.domain.Severity;
import com.codeguard.agent.persistence.CodeRepositoryRepository;
import com.codeguard.agent.persistence.ProjectRepository;
import com.codeguard.agent.persistence.ReviewEntity;
import com.codeguard.agent.persistence.ReviewIssueRepository;
import com.codeguard.agent.persistence.ReviewRepository;
import com.codeguard.agent.security.CurrentUserService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 仪表盘聚合服务。
 *
 * 这里把项目、仓库、审查任务、问题等级汇总成一个接口，
 * 前端首页不需要分别请求多个表再自己拼装。
 */
@Service
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewIssueRepository issueRepository;
    private final ReviewMapper reviewMapper;
    private final CurrentUserService currentUserService;

    public DashboardService(
            ProjectRepository projectRepository,
            CodeRepositoryRepository codeRepositoryRepository,
            ReviewRepository reviewRepository,
            ReviewIssueRepository issueRepository,
            ReviewMapper reviewMapper,
            CurrentUserService currentUserService
    ) {
        this.projectRepository = projectRepository;
        this.codeRepositoryRepository = codeRepositoryRepository;
        this.reviewRepository = reviewRepository;
        this.issueRepository = issueRepository;
        this.reviewMapper = reviewMapper;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto summary() {
        String organizationKey = currentUserService.organizationKey();
        List<ReviewEntity> recent = reviewRepository.findTop100ByOrganizationKeyOrderByCreatedAtDesc(organizationKey);
        long totalReviews = reviewRepository.countByOrganizationKey(organizationKey);

        Map<String, Long> statusCounts = new java.util.LinkedHashMap<>();
        for (ReviewStatus status : ReviewStatus.values()) {
            statusCounts.put(status.name(), reviewRepository.countByOrganizationKeyAndStatus(organizationKey, status));
        }

        double averageRiskScore = recent.stream()
                .mapToInt(ReviewEntity::getRiskScore)
                .average()
                .orElse(0);

        long highRiskReviews = recent.stream()
                .filter(review -> review.getRiskScore() >= 60)
                .count();

        long blockedReviews = recent.stream()
                .filter(review -> review.getRecommendation() == MergeRecommendation.BLOCK
                        || review.getRecommendation() == MergeRecommendation.REQUEST_CHANGES)
                .count();

        List<ReviewListItem> latestReviews = reviewRepository
                .findTop20ByOrganizationKeyOrderByCreatedAtDesc(organizationKey)
                .stream()
                .map(reviewMapper::toListItem)
                .toList();

        return new DashboardSummaryDto(
                organizationKey,
                projectRepository.countByOrganizationKey(organizationKey),
                codeRepositoryRepository.countByOrganizationKey(organizationKey),
                totalReviews,
                statusCounts,
                Math.round(averageRiskScore * 10.0) / 10.0,
                highRiskReviews,
                issueRepository.countByReviewOrganizationKeyAndSeverity(organizationKey, Severity.P0),
                issueRepository.countByReviewOrganizationKeyAndSeverity(organizationKey, Severity.P1),
                recent.isEmpty() ? 0 : Math.round((blockedReviews * 1000.0 / recent.size())) / 10.0,
                latestReviews
        );
    }
}
