package com.codeguard.agent.domain;

/** diff 里的每一行是什么类型 */
public enum DiffLineType {
    ADDITION,
    DELETION,
    CONTEXT
}