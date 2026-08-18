package com.codeguard.agent.domain;

/** 定义一个公开枚举类，表示“合并建议” */
public enum MergeRecommendation {
    APPROVE,
    CAN_MERGE_WITH_NOTES,
    REQUEST_CHANGES,
    BLOCK
}