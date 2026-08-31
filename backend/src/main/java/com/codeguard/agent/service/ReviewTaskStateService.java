package com.codeguard.agent.service;

import com.codeguard.agent.domain.ReviewStatus;
import com.codeguard.agent.persistence.ReviewEntity;
import com.codeguard.agent.persistence.ReviewRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Review 任务状态服务。
 *
 * 异步执行器和队列 worker 都可能触发同一个任务。这里用数据库行锁认领任务，
 * 保证同一时间只有一个执行线程能把 QUEUED 改成 RUNNING。
 */
@Service
public class ReviewTaskStateService {

    private final ReviewRepository reviewRepository;

    public ReviewTaskStateService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public Optional<ReviewEntity> claimQueued(UUID reviewId) {
        Optional<ReviewEntity> candidate = reviewRepository.findByIdForUpdate(reviewId);
        if (candidate.isEmpty() || candidate.get().getStatus() != ReviewStatus.QUEUED) {
            return Optional.empty();
        }

        ReviewEntity review = candidate.get();
        review.start();
        return Optional.of(reviewRepository.save(review));
    }
}
