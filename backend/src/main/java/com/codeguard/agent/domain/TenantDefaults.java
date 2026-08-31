package com.codeguard.agent.domain;

/**
 * 多租户演示环境的默认值。
 *
 * 企业项目通常会按公司、团队或业务线隔离数据。这里先用一个默认组织，
 * 这样本地演示不用额外建账号体系，也能体现“租户隔离”的设计。
 */
public final class TenantDefaults {

    public static final String DEFAULT_ORGANIZATION_KEY = "demo-enterprise";
    public static final String DEFAULT_ORGANIZATION_NAME = "CodeGuard Demo Enterprise";

    private TenantDefaults() {}
}
