package com.codeguard.agent.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GithubPrReviewRequest(
        @NotBlank(message = "repository is required, example: owner/repo")
        @Size(max = 240, message = "repository must be at most 240 characters")
        String repository,

        @Min(value = 1, message = "pullNumber must be greater than 0")
        int pullNumber,

        @Size(max = 80, message = "projectKey must be at most 80 characters")
        String projectKey,

        @Valid
        ReviewOptions options
) {}
