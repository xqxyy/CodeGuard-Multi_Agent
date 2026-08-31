package com.codeguard.agent.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 配置。
 *
 * Springdoc 会自动扫描 Controller 生成接口文档；这个类负责补充项目说明和 Bearer Token 鉴权信息。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI codeGuardOpenApi() {
        return new OpenAPI()
                // 文档首页展示的项目信息。
                .info(new Info()
                        .title("CodeGuard Agent API")
                        .version("0.1.0")
                        .description("多 Agent 代码审查平台接口文档，包含登录、审查任务、项目资产、策略中心、Agent Trace、审计日志和报告导出。"))
                // 声明大多数业务接口需要 Bearer Token。
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .name(BEARER_AUTH)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)));
    }
}
