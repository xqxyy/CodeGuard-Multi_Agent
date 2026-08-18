package com.codeguard.agent.domain;

/** 问题类型：
 * BUG         逻辑缺陷
 * SECURITY    安全问题
 * QUALITY     代码质量问题
 * TEST_GAP    测试缺口
 */
public enum IssueTag {
    BUG,
    SECURITY,
    QUALITY,
    TEST_GAP
}