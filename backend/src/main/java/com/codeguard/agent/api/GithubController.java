package com.codeguard.agent.api;

import com.codeguard.agent.service.GithubPrService;
import com.codeguard.agent.service.ReviewAsyncExecutor;
import com.codeguard.agent.service.ReviewWorkflowService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GitHub 集成接口。
 */
@RestController
@RequestMapping("/api/integrations/github")
public class GithubController {

    private final GithubPrService githubPrService;
    private final ReviewWorkflowService reviewWorkflowService;
    private final ReviewAsyncExecutor reviewAsyncExecutor;

    public GithubController(
            GithubPrService githubPrService,
            ReviewWorkflowService reviewWorkflowService,
            ReviewAsyncExecutor reviewAsyncExecutor
    ) {
        this.githubPrService = githubPrService;
        this.reviewWorkflowService = reviewWorkflowService;
        this.reviewAsyncExecutor = reviewAsyncExecutor;
    }

    @PostMapping("/pr-review")
    public ReviewJobResponse reviewPullRequest(@Valid @RequestBody GithubPrReviewRequest request) {
        ReviewJobResponse job = reviewWorkflowService.submit(githubPrService.toReviewRequest(request));
        reviewAsyncExecutor.execute(job.reviewId());
        return job;
    }
}
