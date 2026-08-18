package com.codeguard.agent.api;

import com.codeguard.agent.domain.ReviewStatus;
import java.util.UUID;

/**
 * 异步审查提交后的返回值。
 *
 * 企业系统里不能让 HTTP 请求一直等 LLM，所以先返回任务编号，再用轮询或事件接口看进度。
 */
public record ReviewJobResponse(
        UUID reviewId,
        ReviewStatus status,
        String pollUrl,
        String detailUrl
) {}
