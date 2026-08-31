package com.codeguard.agent.domain;

/**
 * 企业知识库检索片段。
 *
 * 当前实现先使用内置知识库，后续可以替换为 Milvus、pgvector 或其他向量库。
 */
public record ReviewKnowledgeSnippet(
        String id,
        String title,
        String content,
        String source,
        double score
) {}
