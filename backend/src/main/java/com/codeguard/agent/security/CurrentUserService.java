package com.codeguard.agent.security;

import com.codeguard.agent.domain.TenantDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 当前登录用户上下文。
 *
 * Controller 和 Service 不需要自己解析 Authorization 头，
 * 只要通过这个服务拿到当前用户、角色和组织即可。
 */
@Service
public class CurrentUserService {

    public String organizationKey() {
        return principal().organizationKey();
    }

    public String username() {
        return principal().username();
    }

    public String role() {
        return principal().role();
    }

    private TokenService.TokenPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof TokenService.TokenPrincipal principal) {
            return principal;
        }

        // 测试环境关闭安全过滤器时没有登录态，使用默认组织保持兼容。
        return new TokenService.TokenPrincipal(
                "system",
                "System",
                "SYSTEM",
                TenantDefaults.DEFAULT_ORGANIZATION_KEY
        );
    }
}
