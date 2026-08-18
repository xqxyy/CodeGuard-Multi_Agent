package com.codeguard.agent.api;

import com.codeguard.agent.security.DemoUser;
import com.codeguard.agent.security.DemoUserService;
import com.codeguard.agent.security.TokenService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录接口。
 *
 * 演示系统用内置用户换取 Bearer Token，前端后续请求都带这个 token。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DemoUserService demoUserService;
    private final TokenService tokenService;

    public AuthController(DemoUserService demoUserService, TokenService tokenService) {
        this.demoUserService = demoUserService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        DemoUser user = demoUserService.authenticate(request.username(), request.password())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        return new LoginResponse(
                tokenService.createToken(user),
                "Bearer",
                user.username(),
                user.displayName(),
                user.role()
        );
    }
}
