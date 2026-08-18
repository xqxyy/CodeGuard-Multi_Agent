package com.codeguard.agent.domain;

import java.util.Locale;

/** 用文件路径判断文件类型 */
public enum FileKind {
    JAVA,
    TEST,
    SQL,
    CONFIG,
    MARKDOWN,
    BUILD,
    OTHER;

    public static FileKind fromPath(String path) {
        if (path == null || path.isBlank()) {
            return OTHER;
        }

        String normalized = path.replace('\\', '/').toLowerCase(Locale.ROOT);

        boolean testPath =
                normalized.contains("/src/test/")
                        || normalized.endsWith("test.java")
                        || normalized.endsWith("tests.java")
                        || normalized.contains("/test/");

        if (normalized.endsWith(".java")) {
            return testPath ? TEST : JAVA;
        }

        if (normalized.endsWith(".sql")) {
            return SQL;
        }

        if (normalized.endsWith("pom.xml")
                || normalized.endsWith("build.gradle")
                || normalized.endsWith("build.gradle.kts")
                || normalized.endsWith("settings.gradle")) {
            return BUILD;
        }

        if (normalized.endsWith(".yml")
                || normalized.endsWith(".yaml")
                || normalized.endsWith(".properties")
                || normalized.endsWith(".xml")
                || normalized.endsWith(".json")
                || normalized.endsWith(".toml")) {
            return CONFIG;
        }

        if (normalized.endsWith(".md") || normalized.endsWith(".txt")) {
            return MARKDOWN;
        }

        return OTHER;
    }
}