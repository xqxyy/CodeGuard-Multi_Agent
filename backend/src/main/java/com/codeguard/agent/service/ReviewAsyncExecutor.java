package com.codeguard.agent.service;

import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Review 后台执行器。
 *
 * Controller 创建任务后调用这里，真正的 Agent 编排会在 reviewTaskExecutor 线程池中运行。
 */
@Service
public class ReviewAsyncExecutor {

    private final ReviewWorkflowService reviewWorkflowService;

    public ReviewAsyncExecutor(ReviewWorkflowService reviewWorkflowService) {
        this.reviewWorkflowService = reviewWorkflowService;
    }

    @Async("reviewTaskExecutor")
    public void execute(UUID reviewId) {
        reviewWorkflowService.execute(reviewId);
    }
}
