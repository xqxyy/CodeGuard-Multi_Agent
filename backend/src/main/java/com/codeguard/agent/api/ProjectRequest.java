package com.codeguard.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank(message = "projectKey is required")
        @Size(max = 80, message = "projectKey must be at most 80 characters")
        String projectKey,

        @Size(max = 160, message = "name must be at most 160 characters")
        String name,

        @Size(max = 600, message = "description must be at most 600 characters")
        String description
) {}
