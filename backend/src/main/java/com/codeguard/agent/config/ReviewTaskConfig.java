package com.codeguard.agent.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Review 异步线程池配置。
 *
 * 多 Agent 审查可能会调用 LLM，耗时不可控，所以不能占用 HTTP 请求线程。
 */
@Configuration
public class ReviewTaskConfig {

    @Bean(name = "reviewTaskExecutor")
    public Executor reviewTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("review-agent-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
