package com.codeguard.agent.service;

import com.codeguard.agent.domain.ParsedDiff;
import com.codeguard.agent.domain.ReviewKnowledgeSnippet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 企业知识库检索服务。
 *
 * 当前版本用内置策略文档实现 RAG 式检索，避免演示环境必须部署 Milvus。
 * 这个服务的输出模型已经和向量检索解耦，后续可以把 retrieve 方法替换成 Milvus/pgvector 查询。
 */
@Service
public class EnterpriseKnowledgeBaseService {

    private static final List<PolicyDocument> DOCUMENTS = List.of(
            new PolicyDocument(
                    "authz-boundary",
                    "权限边界变更规范",
                    "删除 @PreAuthorize、permitAll、CSRF 关闭、JWT 过滤器调整都必须补充未授权访问和越权访问测试。",
                    "CodeGuard Built-in Security Policy",
                    List.of("preauthorize", "permitall", "csrf", "jwt", "auth", "role", "securityconfig")
            ),
            new PolicyDocument(
                    "sql-safety",
                    "SQL 安全规范",
                    "SQL 必须使用参数绑定或 ORM 参数化能力，禁止拼接用户输入，迁移脚本需要确认回滚和兼容性。",
                    "CodeGuard Built-in Data Policy",
                    List.of("select ", "insert ", "update ", "delete ", "where", "${", "migration", ".sql")
            ),
            new PolicyDocument(
                    "file-upload",
                    "文件上传安全规范",
                    "上传文件必须校验大小、类型、扩展名和路径归一化，禁止直接信任 getOriginalFilename。",
                    "CodeGuard Built-in Security Policy",
                    List.of("multipartfile", "upload", "transferTo", "getoriginalfilename", "path.normalize")
            ),
            new PolicyDocument(
                    "dependency-governance",
                    "依赖治理规范",
                    "新增依赖必须固定版本、说明用途，并经过安全漏洞和许可证检查。",
                    "CodeGuard Built-in Supply Chain Policy",
                    List.of("pom.xml", "package.json", "package-lock.json", "dependency", "version", "snapshot")
            ),
            new PolicyDocument(
                    "audit-observability",
                    "审计与可观测性规范",
                    "涉及登录、权限、支付、删除、导出、密钥和配置变更的代码必须保留审计日志和请求追踪信息。",
                    "CodeGuard Built-in Operations Policy",
                    List.of("login", "delete", "payment", "export", "token", "secret", "audit", "request-id")
            ),
            new PolicyDocument(
                    "queue-reliability",
                    "异步任务可靠性规范",
                    "队列任务必须具备幂等、超时、重试、失败可观测和重复执行保护。",
                    "CodeGuard Built-in Reliability Policy",
                    List.of("async", "queue", "scheduled", "retry", "idempotency", "executor", "worker")
            )
    );

    public List<ReviewKnowledgeSnippet> retrieve(ParsedDiff parsedDiff, String rawDiff) {
        String corpus = buildCorpus(parsedDiff, rawDiff);

        List<ReviewKnowledgeSnippet> hits = DOCUMENTS.stream()
                .map(document -> toSnippet(document, score(document, corpus)))
                .filter(snippet -> snippet.score() > 0)
                .sorted(Comparator.comparing(ReviewKnowledgeSnippet::score).reversed())
                .limit(5)
                .toList();

        if (!hits.isEmpty()) {
            return hits;
        }

        PolicyDocument fallback = DOCUMENTS.get(0);
        return List.of(new ReviewKnowledgeSnippet(
                fallback.id(),
                fallback.title(),
                fallback.content(),
                fallback.source(),
                0.1
        ));
    }

    private static ReviewKnowledgeSnippet toSnippet(PolicyDocument document, double score) {
        return new ReviewKnowledgeSnippet(
                document.id(),
                document.title(),
                document.content(),
                document.source(),
                score
        );
    }

    private static double score(PolicyDocument document, String corpus) {
        int matches = 0;
        for (String keyword : document.keywords()) {
            if (corpus.contains(keyword.toLowerCase(Locale.ROOT))) {
                matches++;
            }
        }
        return matches == 0 ? 0 : matches / (double) document.keywords().size();
    }

    private static String buildCorpus(ParsedDiff parsedDiff, String rawDiff) {
        StringBuilder builder = new StringBuilder(rawDiff == null ? "" : rawDiff);
        parsedDiff.files().forEach(file -> builder.append('\n').append(file.displayPath()));
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private record PolicyDocument(
            String id,
            String title,
            String content,
            String source,
            List<String> keywords
    ) {}
}
