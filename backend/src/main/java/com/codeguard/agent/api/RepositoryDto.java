package com.codeguard.agent.api;

import java.util.UUID;

public record RepositoryDto(
        UUID id,
        String organizationKey,
        String projectKey,
        String repositoryName,
        String provider,
        String remoteUrl
) {}
