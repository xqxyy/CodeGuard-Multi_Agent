package com.codeguard.agent.service;

/**
 * 请求超过平台治理限制时抛出的异常。
 *
 * API 层会把它转换成 HTTP 429 Too Many Requests。
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
