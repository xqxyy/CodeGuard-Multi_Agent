/** 解析完成后的 diff */
package com.codeguard.agent.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ParsedDiff(
        List<ChangedFile> files,
        DiffSummary summary
) {
    public ParsedDiff {
        files = List.copyOf(files);
    }

    /** of(...) 是静态方法。 根据文件列表自动计算统计信息 */
    public static ParsedDiff of(List<ChangedFile> files) {
        int additions = files.stream()
                .mapToInt(ChangedFile::additions)
                .sum();

        int deletions = files.stream()
                .mapToInt(ChangedFile::deletions)
                .sum();

        Map<FileKind, Long> filesByKind = files.stream()
                .collect(Collectors.groupingBy(ChangedFile::fileKind, Collectors.counting()));

        DiffSummary summary = new DiffSummary(
                files.size(),
                additions,
                deletions,
                filesByKind
        );

        return new ParsedDiff(files, summary);
    }

    public boolean hasProductionJavaChange() {
        return files.stream().anyMatch(ChangedFile::isJavaProductionFile);
    }

    public boolean hasTestChange() {
        return files.stream().anyMatch(ChangedFile::isTestFile);
    }
}