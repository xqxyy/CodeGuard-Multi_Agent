package com.codeguard.agent.api;

import java.time.Instant;
import java.util.UUID;

public record ProjectDto(
        UUID id,
        String projectKey,
        String name,
        String description,
        Instant createdAt
) {}
