package com.codeguard.agent.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 前端或接口传来的 Review 请求。
 *
 * 除了最核心的 title 和 diffText，这里还带上项目、仓库、来源和 Agent 开关。
 * 这样同一个多 Agent 引擎可以服务多个企业项目，而不是只能审查一次临时 diff。
 */
public record ReviewRequest(
        @Size(max = 200, message = "title must be at most 200 characters")
        String title,

        @NotBlank(message = "diffText is required")
        String diffText,

        @Size(max = 80, message = "projectKey must be at most 80 characters")
        String projectKey,

        @Size(max = 180, message = "repositoryName must be at most 180 characters")
        String repositoryName,

        @Size(max = 40, message = "sourceType must be at most 40 characters")
        String sourceType,

        @Size(max = 600, message = "sourceUrl must be at most 600 characters")
        String sourceUrl,

        @Valid
        ReviewOptions options
) {
    /**
     * 兼容旧调用：测试或简单接口仍然可以只传 title 和 diffText。
     */
    public ReviewRequest(String title, String diffText) {
        this(title, diffText, null, null, null, null, null);
    }

    public String normalizedProjectKey() {
        return projectKey == null || projectKey.isBlank() ? "default" : projectKey.strip();
    }

    public String normalizedRepositoryName() {
        return repositoryName == null || repositoryName.isBlank() ? "manual-diff" : repositoryName.strip();
    }

    public String normalizedSourceType() {
        return sourceType == null || sourceType.isBlank() ? "MANUAL" : sourceType.strip();
    }

    public ReviewOptions normalizedOptions() {
        return options == null ? ReviewOptions.defaults() : options.withDefaults();
    }
}
