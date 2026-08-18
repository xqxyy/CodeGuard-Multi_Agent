package com.codeguard.agent.sample;

import java.util.List;

/**
 * 内置 diff 样例对象
 *
 * 每个 SampleDiff 表示一个可以直接拿来审查的 Git diff 示例
 */
public record SampleDiff(
        String id,
        String title,
        String category,
        String description,
        List<String> expectedTags,
        String diffText
) {

    /**
     * 构造时复制标签列表
     *
     * 避免外部代码修改 expectedTags，保证样例对象稳定
     */
    public SampleDiff {
        expectedTags = List.copyOf(expectedTags);
    }
}