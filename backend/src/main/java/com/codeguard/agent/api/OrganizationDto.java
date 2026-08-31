package com.codeguard.agent.api;

import java.time.Instant;
import java.util.UUID;

public record OrganizationDto(
        UUID id,
        String organizationKey,
        String name,
        String description,
        String plan,
        Instant createdAt
) {}
