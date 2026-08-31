package com.codeguard.agent.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {

    List<AuditLogEntity> findTop50ByOrganizationKeyOrderByCreatedAtDesc(String organizationKey);
}
