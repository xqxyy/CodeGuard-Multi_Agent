package com.codeguard.agent.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Review 主记录仓储
 *
 * JpaRepository 提供基础增删改查能力
 */
public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

    /**
     * 查询最近 20 条 Review
     */
    List<ReviewEntity> findTop20ByOrderByCreatedAtDesc();

    /**
     * 查询某个项目最近 20 条 Review。
     */
    List<ReviewEntity> findTop20ByProjectKeyOrderByCreatedAtDesc(String projectKey);
}
