package com.codeguard.agent.domain;

/** 严重成都 */
public enum Severity {
    P0(40),
    P1(25),
    P2(12),
    P3(5);

    private final int weight;

    Severity(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}