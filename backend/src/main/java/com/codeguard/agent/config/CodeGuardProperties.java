package com.codeguard.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CodeGuard 系统配置类
 *
 * 这个类负责读取 application.yml 或环境变量里的配置
 * 例如最大 diff 长度、LLM 提供商、OpenAI API Key、模型名称等
 */
@ConfigurationProperties(prefix = "codeguard")
public record CodeGuardProperties(
        Review review,
        Llm llm
) {

    /**
     * 主配置的兜底逻辑
     *
     * 如果配置文件里没有写 review 或 llm，就给默认值
     */
    public CodeGuardProperties {
        if (review == null) {
            review = new Review(200_000);
        }

        if (llm == null) {
            llm = new Llm(
                    "mock",
                    new OpenAi("", "https://api.openai.com/v1", "gpt-4o-mini")
            );
        }
    }

    /**
     * 审查执行配置
     *
     * maxDiffChars 用来限制 diff 最大长度，避免一次提交太大导致模型或服务压力过高
     */
    public record Review(
            int maxDiffChars
    ) {

        /**
         * 审查配置兜底逻辑
         */
        public Review {
            if (maxDiffChars <= 0) {
                maxDiffChars = 200_000;
            }
        }
    }

    /**
     * LLM 总配置
     *
     * provider 表示当前使用哪个模型提供商
     * 当前先支持 mock 和 openai
     */
    public record Llm(
            String provider,
            OpenAi openai
    ) {

        /**
         * LLM 配置兜底逻辑
         */
        public Llm {
            if (provider == null || provider.isBlank()) {
                provider = "mock";
            }

            if (openai == null) {
                openai = new OpenAi("", "https://api.openai.com/v1", "gpt-4o-mini");
            }
        }
    }

    /**
     * OpenAI 或 OpenAI 兼容接口配置
     *
     * apiKey 是密钥
     * baseUrl 是接口地址
     * model 是模型名称
     */
    public record OpenAi(
            String apiKey,
            String baseUrl,
            String model
    ) {

        /**
         * OpenAI 配置兜底逻辑
         */
        public OpenAi {
            if (baseUrl == null || baseUrl.isBlank()) {
                baseUrl = "https://api.openai.com/v1";
            }

            if (model == null || model.isBlank()) {
                model = "gpt-4o-mini";
            }
        }
    }
}