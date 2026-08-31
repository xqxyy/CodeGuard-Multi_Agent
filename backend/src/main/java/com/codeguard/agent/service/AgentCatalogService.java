package com.codeguard.agent.service;

import com.codeguard.agent.api.AgentCatalogItem;
import com.codeguard.agent.config.CodeGuardChatModelProvider;
import com.codeguard.agent.domain.AgentType;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Agent 目录服务。
 *
 * 它不执行审查，只描述平台里有哪些 Agent、每个 Agent 负责什么。
 * 前端用它展示企业级 Agent 编排能力。
 */
@Service
public class AgentCatalogService {

    private final CodeGuardChatModelProvider chatModelProvider;

    public AgentCatalogService(CodeGuardChatModelProvider chatModelProvider) {
        this.chatModelProvider = chatModelProvider;
    }

    public List<AgentCatalogItem> listAgents() {
        String llmStatus = chatModelProvider.configured() ? "READY" : "NEEDS_API_KEY";

        return List.of(
                new AgentCatalogItem(
                        AgentType.ROUTER,
                        "Router Agent",
                        "分析文件类型和变更范围，决定哪些专业 Agent 需要参与。",
                        "Rule engine",
                        "READY",
                        false,
                        List.of("文件类型", "变更规模", "风险路径"),
                        "减少无效扫描，让审查链路更快、更可解释。"
                ),
                new AgentCatalogItem(
                        AgentType.CONTEXT_ENRICHMENT,
                        "Context Tool Agent",
                        "汇总变更文件、风险路径、测试配套和关键上下文，供后续 Agent 使用。",
                        "Tool orchestrator",
                        "READY",
                        false,
                        List.of("文件索引", "风险路径", "测试发现"),
                        "让审查从只看 diff 升级到带上下文的仓库级判断。"
                ),
                new AgentCatalogItem(
                        AgentType.BUG_LOGIC,
                        "Bug Logic Agent",
                        "识别 return null、空指针、异常吞掉等常见逻辑风险。",
                        "Static rules",
                        "READY",
                        false,
                        List.of("空返回", "异常处理", "边界条件"),
                        "提前拦截会直接影响线上稳定性的代码缺陷。"
                ),
                new AgentCatalogItem(
                        AgentType.SECURITY,
                        "Security Agent",
                        "检查密钥泄露、SQL 拼接、危险日志和认证绕过风险。",
                        "Static rules",
                        "READY",
                        false,
                        List.of("Secret", "SQL 注入", "鉴权绕过"),
                        "把安全问题从上线后整改前移到代码合并前。"
                ),
                new AgentCatalogItem(
                        AgentType.CODE_QUALITY,
                        "Code Quality Agent",
                        "识别重复逻辑、过长函数、魔法值和可维护性问题。",
                        "Static rules",
                        "READY",
                        false,
                        List.of("可维护性", "复杂度", "重复代码"),
                        "帮助团队保持长期可维护的代码质量基线。"
                ),
                new AgentCatalogItem(
                        AgentType.TEST_COVERAGE,
                        "Test Coverage Agent",
                        "检查接口、服务和关键逻辑变更是否同步补充测试。",
                        "Static rules",
                        "READY",
                        false,
                        List.of("测试文件", "接口变更", "回归风险"),
                        "降低未测试代码进入主干的概率。"
                ),
                new AgentCatalogItem(
                        AgentType.STATIC_ANALYSIS,
                        "Enterprise Static Analysis Agent",
                        "执行供应链、线程治理、危险进程退出和安全 TODO 等企业静态规则。",
                        "Static analyzer adapter",
                        "READY",
                        false,
                        List.of("Semgrep-ready", "依赖治理", "线程池治理"),
                        "把成熟扫描器和企业规则统一纳入 CodeGuard 问题模型。"
                ),
                new AgentCatalogItem(
                        AgentType.KNOWLEDGE_BASE,
                        "Knowledge Base Agent",
                        "检索企业安全、供应链、审计和可靠性规范，并对照本次 diff 生成风险。",
                        "Embedded RAG",
                        "READY",
                        true,
                        List.of("企业规范", "历史经验", "RAG-ready"),
                        "让审查结果能引用企业制度和历史经验，而不是只给通用建议。"
                ),
                new AgentCatalogItem(
                        AgentType.LLM_REVIEW,
                        "LLM Review Agent",
                        "结合规则 Agent 结果和 Git Diff 做综合推理审查。",
                        chatModelProvider.providerName() + " / " + chatModelProvider.modelName(),
                        llmStatus,
                        true,
                        List.of("上下文理解", "综合建议", "结构化 JSON"),
                        "补足纯规则难以覆盖的语义理解和综合判断。"
                ),
                new AgentCatalogItem(
                        AgentType.SUMMARY,
                        "Summary Agent",
                        "汇总问题、风险分、合并建议和 Markdown 报告。",
                        "Rule engine",
                        "READY",
                        false,
                        List.of("风险评分", "合并建议", "审查报告"),
                        "把多个 Agent 输出整理成管理层和研发都能读懂的结果。"
                )
        );
    }
}
