package com.codeguard.agent.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求追踪 ID 过滤器。
 *
 * 每个请求都会得到一个 X-Request-Id，前端报错、后端日志和接口返回可以用同一个 ID 对齐。
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    public static String currentRequestId() {
        String requestId = MDC.get(MDC_KEY);
        return requestId == null || requestId.isBlank() ? null : requestId;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = normalize(request.getHeader(HEADER_NAME));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String stripped = value.strip();
        // 控制长度，避免客户端塞入超长 header 污染日志。
        return stripped.length() <= 80 ? stripped : stripped.substring(0, 80);
    }
}
