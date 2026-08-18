package com.codeguard.agent.domain;

/**
 * Review 来源类型。
 *
 * 企业系统里必须知道审查来自哪里：手动粘贴、内置样例，还是 GitHub PR。
 */
public enum ReviewSourceType {
    MANUAL,
    SAMPLE,
    GITHUB_PR
}
