package com.codeguard.agent.service;

import com.codeguard.agent.config.CodeGuardProperties;
import com.codeguard.agent.domain.ReviewStatus;
import com.codeguard.agent.persistence.ReviewRepository;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Review 队列兜底 worker。
 *
 * Controller 提交任务后会立即触发异步执行；这个 worker 负责兜底扫描数据库里
 * 仍然处于 QUEUED 的任务，避免任务因为进程抖动而永远停在排队状态。
 */
@Component
public class ReviewQueueWorker {

    private final ReviewRepository reviewRepository;
    private final ReviewAsyncExecutor reviewAsyncExecutor;
    private final CodeGuardProperties properties;

    public ReviewQueueWorker(
            ReviewRepository reviewRepository,
            ReviewAsyncExecutor reviewAsyncExecutor,
            CodeGuardProperties properties
    ) {
        this.reviewRepository = reviewRepository;
        this.reviewAsyncExecutor = reviewAsyncExecutor;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${codeguard.review.queue-initial-delay-ms:5000}",
            fixedDelayString = "${codeguard.review.queue-poll-ms:3000}"
    )
    public void dispatchQueuedReviews() {
        reviewRepository.findTop5ByStatusOrderByCreatedAtAsc(ReviewStatus.QUEUED)
                .stream()
                .limit(properties.review().queuedDispatchBatchSize())
                .forEach(review -> reviewAsyncExecutor.execute(review.getId()));
    }

    @Scheduled(
            initialDelayString = "${codeguard.review.stalled-initial-delay-ms:60000}",
            fixedDelayString = "${codeguard.review.stalled-poll-ms:60000}"
    )
    public void failStalledRunningReviews() {
        Instant cutoff = Instant.now().minusSeconds(properties.review().runningTimeoutMinutes() * 60L);
        var stalledReviews = reviewRepository.findTop20ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
                ReviewStatus.RUNNING,
                cutoff
        );
        stalledReviews.forEach(review -> review.fail(
                "Review exceeded running timeout of "
                        + properties.review().runningTimeoutMinutes()
                        + " minutes"
        ));
        reviewRepository.saveAll(stalledReviews);
    }
}
