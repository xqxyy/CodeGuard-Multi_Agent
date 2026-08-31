package com.codeguard.agent.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    Optional<ProjectEntity> findByProjectKey(String projectKey);

    Optional<ProjectEntity> findByOrganizationKeyAndProjectKey(String organizationKey, String projectKey);

    List<ProjectEntity> findAllByOrderByCreatedAtAsc();

    List<ProjectEntity> findByOrganizationKeyOrderByCreatedAtAsc(String organizationKey);

    long countByOrganizationKey(String organizationKey);
}
