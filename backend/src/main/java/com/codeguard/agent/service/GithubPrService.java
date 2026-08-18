package com.codeguard.agent.service;

import com.codeguard.agent.api.GithubPrReviewRequest;
import com.codeguard.agent.api.ReviewRequest;
import com.codeguard.agent.domain.ReviewSourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * GitHub PR 集成服务。
 *
 * 演示时输入 owner/repo 和 PR 编号，系统会直接拉取 GitHub diff，再交给多 Agent 审查。
 */
@Service
public class GithubPrService {

    private final RestClient restClient;
    private final String githubToken;

    public GithubPrService(
            RestClient.Builder restClientBuilder,
            @Value("${codeguard.github.token:}") String githubToken
    ) {
        this.restClient = restClientBuilder.build();
        this.githubToken = githubToken;
    }

    public ReviewRequest toReviewRequest(GithubPrReviewRequest request) {
        String repository = request.repository().strip();
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("repository must look like owner/repo");
        }

        String prUrl = "https://github.com/" + repository + "/pull/" + request.pullNumber();
        String apiUrl = "https://api.github.com/repos/" + repository + "/pulls/" + request.pullNumber();
        String diffText = fetchDiff(apiUrl);

        return new ReviewRequest(
                "GitHub PR #" + request.pullNumber() + " - " + repository,
                diffText,
                request.projectKey() == null || request.projectKey().isBlank() ? "github-demo" : request.projectKey(),
                repository,
                ReviewSourceType.GITHUB_PR.name(),
                prUrl,
                request.options()
        );
    }

    private String fetchDiff(String apiUrl) {
        RestClient.RequestHeadersSpec<?> spec = restClient.get()
                .uri(apiUrl)
                .accept(MediaType.valueOf("application/vnd.github.v3.diff"))
                .header(HttpHeaders.USER_AGENT, "CodeGuard-Agent-Demo");

        if (githubToken != null && !githubToken.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken);
        }

        return spec.retrieve().body(String.class);
    }
}
