package com.codeguard.agent.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Review 问题仓储
 */
public interface ReviewIssueRepository extends JpaRepository<ReviewIssueEntity, UUID> {

    /**
     * 查询某次 Review 下的所有问题
     */
    List<ReviewIssueEntity> findByReviewIdOrderByCreatedAtAsc(UUID reviewId);

    @Transactional
    void deleteByReviewId(UUID reviewId);
}
