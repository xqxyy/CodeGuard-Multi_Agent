package com.codeguard.agent.persistence;

import com.codeguard.agent.domain.ReviewStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Review 主记录仓储。
 *
 * Spring Data JPA 会根据方法名自动生成查询 SQL。
 */
public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

    List<ReviewEntity> findTop20ByOrderByCreatedAtDesc();

    List<ReviewEntity> findTop20ByOrganizationKeyOrderByCreatedAtDesc(String organizationKey);

    List<ReviewEntity> findTop20ByProjectKeyOrderByCreatedAtDesc(String projectKey);

    List<ReviewEntity> findTop20ByOrganizationKeyAndProjectKeyOrderByCreatedAtDesc(
            String organizationKey,
            String projectKey
    );

    List<ReviewEntity> findTop100ByOrganizationKeyOrderByCreatedAtDesc(String organizationKey);

    List<ReviewEntity> findTop5ByStatusOrderByCreatedAtAsc(ReviewStatus status);

    List<ReviewEntity> findTop20ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            ReviewStatus status,
            Instant updatedAt
    );

    Optional<ReviewEntity> findFirstByOrganizationKeyAndIdempotencyKey(
            String organizationKey,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select review from ReviewEntity review where review.id = :id")
    Optional<ReviewEntity> findByIdForUpdate(@Param("id") UUID id);

    long countByOrganizationKey(String organizationKey);

    long countByOrganizationKeyAndStatus(String organizationKey, ReviewStatus status);

    long countByOrganizationKeyAndStatusIn(String organizationKey, Collection<ReviewStatus> statuses);
}
