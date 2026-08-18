package com.codeguard.agent.security;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 演示用户服务。
 *
 * 密码只用于本地演示；生产环境应该替换成数据库用户或公司统一身份系统。
 */
@Service
public class DemoUserService {

    private final List<DemoUser> users = List.of(
            new DemoUser("admin", "codeguard123", "平台管理员", "ADMIN"),
            new DemoUser("developer", "developer123", "研发用户", "DEVELOPER"),
            new DemoUser("auditor", "auditor123", "审计用户", "AUDITOR")
    );

    public Optional<DemoUser> authenticate(String username, String password) {
        return users.stream()
                .filter(user -> user.username().equals(username))
                .filter(user -> user.password().equals(password))
                .findFirst();
    }
}
