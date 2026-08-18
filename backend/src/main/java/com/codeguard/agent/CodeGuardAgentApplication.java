package com.codeguard.agent;

import com.codeguard.agent.config.CodeGuardProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * CodeGuard-Agent 后端启动类
 *
 * 这是整个 Spring Boot 项目的入口
 * 程序从 main 方法开始运行
 */
@SpringBootApplication
@EnableConfigurationProperties(CodeGuardProperties.class)
@EnableAsync
public class CodeGuardAgentApplication {

    /**
     * Java 程序入口方法
     *
     * SpringApplication.run 会启动 Spring 容器和内置 Web 服务
     */
    public static void main(String[] args) {
        SpringApplication.run(CodeGuardAgentApplication.class, args);
    }
}
