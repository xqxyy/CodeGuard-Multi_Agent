package com.codeguard.agent.api;

import java.time.Instant;
import java.util.UUID;

public record AuditLogDto(
        UUID id,
        String organizationKey,
        String actorUsername,
        String actorRole,
        String action,
        String resourceType,
        String resourceId,
        String summary,
        Instant createdAt
) {}
