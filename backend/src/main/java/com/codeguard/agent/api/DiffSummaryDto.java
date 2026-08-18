/** 返回给前端的 diff 摘要 */
package com.codeguard.agent.api;

import java.util.Map;

public record DiffSummaryDto(
        int filesChanged,
        int additions,
        int deletions,
        Map<String, Long> filesByKind
) {}