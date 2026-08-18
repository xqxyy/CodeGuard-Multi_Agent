/** 一次 diff 的统计信息 */
package com.codeguard.agent.domain;

import java.util.Map;

public record DiffSummary(
        int filesChanged,
        int additions,
        int deletions,
        Map<FileKind, Long> filesByKind
) {
    public DiffSummary {
        filesByKind = Map.copyOf(filesByKind);
    }
}