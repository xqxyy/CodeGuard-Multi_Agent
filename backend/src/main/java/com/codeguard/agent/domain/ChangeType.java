package com.codeguard.agent.domain;

/** 文件是新增、修改、删除还是重命名 */
public enum ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED,
    UNKNOWN
}