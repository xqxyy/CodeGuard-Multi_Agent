package com.codeguard.agent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 大模型提供器。
 *
 * 这个类负责根据配置创建 LangChain4j 的 ChatModel。
 * DeepSeek、Kimi、GLM 等模型只要兼容 OpenAI 协议，就可以通过 OpenAiChatModel 调用。
 */
@Component
public class CodeGuardChatModelProvider {

    /** 缓存已经创建好的模型客户端，避免每次 Review 都重新初始化。 */
    private ChatModel cachedModel;

    /**
     * 获取聊天模型。
     *
     * 配置优先级：
     * 1. 环境变量 CODEGUARD_LLM_*
     * 2. 本地私有类 CodeGuardLocalLlmConfig
     * 3. 公开默认类 CodeGuardLlmConfig
     *
     * 如果最终没有 API Key，就返回 Optional.empty，LLM Agent 会自动跳过。
     */
    public Optional<ChatModel> chatModel() {
        LlmSettings settings = LlmSettings.resolve();
        if (!settings.hasApiKey()) {
            return Optional.empty();
        }

        if (cachedModel == null) {
            cachedModel = OpenAiChatModel.builder()
                    .apiKey(settings.apiKey())
                    .baseUrl(resolveBaseUrl(settings))
                    .modelName(resolveModel(settings))
                    .temperature(0.1)
                    .responseFormat("json_object")
                    .timeout(Duration.ofSeconds(30))
                    .maxRetries(1)
                    .build();
        }

        return Optional.of(cachedModel);
    }

    public String providerName() {
        return LlmSettings.resolve().provider();
    }

    public String modelName() {
        return resolveModel(LlmSettings.resolve());
    }

    /** 根据供应商决定接口地址。用户显式配置 BASE_URL 时，优先使用配置值。 */
    private String resolveBaseUrl(LlmSettings settings) {
        if (settings.baseUrl() != null && !settings.baseUrl().isBlank()) {
            return settings.baseUrl();
        }

        return switch (settings.provider().toLowerCase(Locale.ROOT)) {
            case "deepseek" -> "https://api.deepseek.com";
            case "kimi" -> "https://api.moonshot.ai/v1";
            case "glm" -> "https://api.z.ai/api/coding/paas/v4";
            case "openai" -> "https://api.openai.com/v1";
            default -> throw new IllegalArgumentException(
                    "未知 LLM provider，请在 BASE_URL 中手动填写 OpenAI 兼容接口地址"
            );
        };
    }

    /** 解析模型名。用户没有显式配置时，按供应商给一个默认模型。 */
    private String resolveModel(LlmSettings settings) {
        if (settings.model() != null && !settings.model().isBlank()) {
            return settings.model();
        }

        return switch (settings.provider().toLowerCase(Locale.ROOT)) {
            case "deepseek" -> "deepseek-v4-flash";
            case "kimi" -> "kimi-k2.6";
            case "glm" -> "glm-5.2";
            case "openai" -> "gpt-4o-mini";
            default -> "deepseek-v4-flash";
        };
    }

    /**
     * LLM 配置快照。
     *
     * record 是 Java 的轻量数据类，适合放一组只读配置值。
     */
    private record LlmSettings(String provider, String apiKey, String baseUrl, String model) {

        static LlmSettings resolve() {
            LlmSettings local = loadLocalSettings().orElse(new LlmSettings(
                    CodeGuardLlmConfig.PROVIDER,
                    CodeGuardLlmConfig.API_KEY,
                    CodeGuardLlmConfig.BASE_URL,
                    CodeGuardLlmConfig.MODEL
            ));

            return new LlmSettings(
                    firstNonBlank(System.getenv("CODEGUARD_LLM_PROVIDER"), local.provider()),
                    firstNonBlank(System.getenv("CODEGUARD_LLM_API_KEY"), local.apiKey()),
                    firstNonBlank(System.getenv("CODEGUARD_LLM_BASE_URL"), local.baseUrl()),
                    firstNonBlank(System.getenv("CODEGUARD_LLM_MODEL"), local.model())
            );
        }

        boolean hasApiKey() {
            return apiKey != null
                    && !apiKey.isBlank()
                    && !"在这里填写你的 API Key".equals(apiKey)
                    && !"<redacted>".equalsIgnoreCase(apiKey);
        }

        private static Optional<LlmSettings> loadLocalSettings() {
            try {
                Class<?> clazz = Class.forName("com.codeguard.agent.config.CodeGuardLocalLlmConfig");
                return Optional.of(new LlmSettings(
                        readString(clazz, "PROVIDER"),
                        readString(clazz, "API_KEY"),
                        readString(clazz, "BASE_URL"),
                        readString(clazz, "MODEL")
                ));
            } catch (ClassNotFoundException exception) {
                return Optional.empty();
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("CodeGuardLocalLlmConfig 配置读取失败", exception);
            }
        }

        private static String readString(Class<?> clazz, String fieldName) throws ReflectiveOperationException {
            Field field = clazz.getField(fieldName);
            Object value = field.get(null);
            return value instanceof String string ? string : "";
        }

        private static String firstNonBlank(String preferred, String fallback) {
            return preferred != null && !preferred.isBlank() ? preferred : fallback;
        }
    }
}
