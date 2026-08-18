package com.codeguard.agent.domain;

/**
 * Review 状态枚举
 *
 * 当前 MVP 主要使用 COMPLETED
 * 后面如果做异步任务，可以扩展 RUNNING、FAILED
 */
public enum ReviewStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED
}
