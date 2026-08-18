package com.codeguard.agent.api;

import java.util.List;

/**
 * 样例 diff 接口返回对象
 *
 * 这个 DTO 用来把内置样例返回给前端或接口调用方
 */
public record SampleDiffDto(
        String id,
        String title,
        String category,
        String description,
        List<String> expectedTags,
        String diffText
) {}