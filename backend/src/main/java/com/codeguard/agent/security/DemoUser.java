package com.codeguard.agent.security;

/**
 * 演示用户。
 *
 * 真实企业环境通常接入 LDAP、OAuth2、企业 SSO；这里先用内置用户方便本地演示。
 */
public record DemoUser(
        String username,
        String password,
        String displayName,
        String role
) {}
