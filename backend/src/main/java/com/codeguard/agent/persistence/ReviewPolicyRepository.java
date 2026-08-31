package com.codeguard.agent.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewPolicyRepository extends JpaRepository<ReviewPolicyEntity, UUID> {

    Optional<ReviewPolicyEntity> findByOrganizationKeyAndProjectKey(String organizationKey, String projectKey);

    List<ReviewPolicyEntity> findByOrganizationKeyOrderByUpdatedAtDesc(String organizationKey);
}
