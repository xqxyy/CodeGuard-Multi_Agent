package com.codeguard.agent.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeRepositoryRepository extends JpaRepository<CodeRepositoryEntity, UUID> {
    Optional<CodeRepositoryEntity> findByProjectProjectKeyAndRepositoryName(String projectKey, String repositoryName);

    Optional<CodeRepositoryEntity> findByOrganizationKeyAndProjectProjectKeyAndRepositoryName(
            String organizationKey,
            String projectKey,
            String repositoryName
    );

    List<CodeRepositoryEntity> findByProjectProjectKeyOrderByCreatedAtAsc(String projectKey);

    List<CodeRepositoryEntity> findByOrganizationKeyAndProjectProjectKeyOrderByCreatedAtAsc(
            String organizationKey,
            String projectKey
    );

    long countByOrganizationKey(String organizationKey);
}
