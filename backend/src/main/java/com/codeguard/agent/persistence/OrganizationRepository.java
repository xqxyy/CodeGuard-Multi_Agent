package com.codeguard.agent.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<OrganizationEntity, UUID> {

    Optional<OrganizationEntity> findByOrganizationKey(String organizationKey);

    List<OrganizationEntity> findAllByOrderByCreatedAtAsc();
}
