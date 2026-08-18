package com.codeguard.agent.config;

/**
 * 大模型默认配置。
 *
 * 这个文件会上传到 GitHub，所以只能放安全的默认值，不能填写真实 API Key。
 * 本地需要调用真实模型时，优先使用环境变量，或者创建被 .gitignore 忽略的
 * CodeGuardLocalLlmConfig.java。
 */
public final class CodeGuardLlmConfig {

    /**
     * 当前使用的模型供应商。
     *
     * 可选值：deepseek、kimi、glm、openai、custom。
     */
    public static final String PROVIDER = "deepseek";

    /**
     * 公开仓库里必须保持为空。
     *
     * 如果这里为空，系统会自动跳过 LLM 审查，只运行规则 Agent。
     */
    public static final String API_KEY = "";

    /**
     * 模型接口地址。
     *
     * 留空时会根据 PROVIDER 自动选择默认地址。
     */
    public static final String BASE_URL = "";

    /**
     * 模型名称。
     *
     * 留空时会根据 PROVIDER 自动选择默认模型名。
     */
    public static final String MODEL = "";

    private CodeGuardLlmConfig() {}
}
