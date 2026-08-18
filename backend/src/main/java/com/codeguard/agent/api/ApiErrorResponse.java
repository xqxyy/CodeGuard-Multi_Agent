package com.codeguard.agent.api;

import java.time.Instant;

/**
 * API 错误返回对象
 *
 * 所有接口异常都统一返回这个结构s
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message
) {}