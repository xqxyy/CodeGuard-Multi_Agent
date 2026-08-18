package com.codeguard.agent.domain;

import java.util.List;

/** diff 里的一个代码块 */
public record DiffHunk(
        int oldStart,
        int oldCount,
        int newStart,
        int newCount,
        String header,
        List<DiffLine> lines
) {
    public DiffHunk {
        lines = List.copyOf(lines);
    }
}