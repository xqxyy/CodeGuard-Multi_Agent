package com.codeguard.agent.security;

import com.codeguard.agent.domain.TenantDefaults;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 演示用户服务。
 *
 * 生产环境通常会接入 LDAP、OAuth2、企业 SSO 或自建用户中心。
 * 这里保留三类角色，方便本地演示权限差异。
 */
@Service
public class DemoUserService {

    private final List<DemoUser> users = List.of(
            new DemoUser("admin", "codeguard123", "平台管理员", "ADMIN", TenantDefaults.DEFAULT_ORGANIZATION_KEY),
            new DemoUser("developer", "developer123", "研发用户", "DEVELOPER", TenantDefaults.DEFAULT_ORGANIZATION_KEY),
            new DemoUser("auditor", "auditor123", "审计用户", "AUDITOR", TenantDefaults.DEFAULT_ORGANIZATION_KEY)
    );

    public Optional<DemoUser> authenticate(String username, String password) {
        return users.stream()
                .filter(user -> user.username().equals(username))
                .filter(user -> user.password().equals(password))
                .findFirst();
    }
}
