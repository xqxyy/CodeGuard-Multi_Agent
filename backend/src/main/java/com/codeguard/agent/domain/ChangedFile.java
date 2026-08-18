/** 一次 diff 里的一个文件变更 */
package com.codeguard.agent.domain;

import java.util.List;

public record ChangedFile(
        String oldPath,
        String newPath,
        ChangeType changeType,
        FileKind fileKind,
        int additions,
        int deletions,
        List<DiffHunk> hunks
) {
    public ChangedFile {
        hunks = List.copyOf(hunks);
    }

    public String displayPath() {
        if (newPath != null && !newPath.equals("/dev/null")) {
            return newPath;
        }
        return oldPath == null ? "(unknown)" : oldPath;
    }

    public boolean isJavaProductionFile() {
        return fileKind == FileKind.JAVA;
    }

    public boolean isTestFile() {
        return fileKind == FileKind.TEST;
    }
}