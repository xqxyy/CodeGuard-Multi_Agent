package com.codeguard.agent.service;

import com.codeguard.agent.config.CodeGuardProperties;
import com.codeguard.agent.domain.ReviewStatus;
import com.codeguard.agent.persistence.ReviewRepository;
import com.codeguard.agent.security.CurrentUserService;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Review 入口治理。
 *
 * 这里负责提交频率限制和组织级并发任务限制，避免高频提交把线程池、数据库或模型接口打满。
 */
@Service
public class ReviewTrafficGuard {

    private static final List<ReviewStatus> ACTIVE_STATUSES = List.of(ReviewStatus.QUEUED, ReviewStatus.RUNNING);

    private final ReviewRepository reviewRepository;
    private final CodeGuardProperties properties;
    private final CurrentUserService currentUserService;
    private final ConcurrentHashMap<String, MinuteBucket> submitBuckets = new ConcurrentHashMap<>();

    public ReviewTrafficGuard(
            ReviewRepository reviewRepository,
            CodeGuardProperties properties,
            CurrentUserService currentUserService
    ) {
        this.reviewRepository = reviewRepository;
        this.properties = properties;
        this.currentUserService = currentUserService;
    }

    /**
     * 提交 Review 前调用。
     *
     * 先限制单用户提交频率，再限制单组织活跃任务数。
     */
    public void assertCanSubmit(String organizationKey) {
        assertSubmitRateAllowed(organizationKey);
        assertActiveTaskCapacity(organizationKey);
    }

    private void assertSubmitRateAllowed(String organizationKey) {
        int limit = properties.review().submitRateLimitPerMinute();
        String bucketKey = organizationKey + ":" + currentUserService.username();
        long currentMinute = Instant.now().getEpochSecond() / 60;

        MinuteBucket bucket = submitBuckets.computeIfAbsent(bucketKey, ignored -> new MinuteBucket());
        if (!bucket.tryAcquire(currentMinute, limit)) {
            throw new RateLimitExceededException(
                    "提交过于频繁，请稍后再试。当前限制为每个用户每分钟 " + limit + " 次。",
                    60
            );
        }
    }

    private void assertActiveTaskCapacity(String organizationKey) {
        int limit = properties.review().maxActiveReviewsPerOrganization();
        long activeCount = reviewRepository.countByOrganizationKeyAndStatusIn(organizationKey, ACTIVE_STATUSES);
        if (activeCount >= limit) {
            throw new RateLimitExceededException(
                    "当前组织排队或运行中的审查任务已达到上限 " + limit + " 个，请等待部分任务完成后再提交。",
                    10
            );
        }
    }

    /**
     * 简单的单机分钟窗口计数器。
     *
     * 生产多实例部署时，应把这个状态迁移到 Redis 或网关限流组件中。
     */
    private static class MinuteBucket {
        private long windowMinute;
        private int count;

        synchronized boolean tryAcquire(long currentMinute, int limit) {
            if (currentMinute != windowMinute) {
                windowMinute = currentMinute;
                count = 0;
            }
            if (count >= limit) {
                return false;
            }
            count++;
            return true;
        }
    }
}
