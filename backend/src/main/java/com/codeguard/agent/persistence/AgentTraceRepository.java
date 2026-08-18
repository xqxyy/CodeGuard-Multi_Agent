package com.codeguard.agent.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent Trace 仓储
 */
public interface AgentTraceRepository extends JpaRepository<AgentTraceEntity, UUID> {

    /**
     * 查询某次 Review 下的所有 Trace
     */
    List<AgentTraceEntity> findByReviewIdOrderByStartedAtAsc(UUID reviewId);

    @Transactional
    void deleteByReviewId(UUID reviewId);
}
