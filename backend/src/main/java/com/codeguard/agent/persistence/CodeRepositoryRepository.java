package com.codeguard.agent.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeRepositoryRepository extends JpaRepository<CodeRepositoryEntity, UUID> {
    Optional<CodeRepositoryEntity> findByProjectProjectKeyAndRepositoryName(String projectKey, String repositoryName);

    List<CodeRepositoryEntity> findByProjectProjectKeyOrderByCreatedAtAsc(String projectKey);
}
