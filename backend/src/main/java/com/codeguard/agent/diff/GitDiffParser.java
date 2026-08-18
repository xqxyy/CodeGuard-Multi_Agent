/** 把原始 Git diff 文本
    解析成 ParsedDiff 对象
    字符串 diff -> ChangedFile -> DiffHunk -> DiffLine
    解析出改了哪些文件、哪些代码块、哪些行是新增/删除
 */
package com.codeguard.agent.diff;

import com.codeguard.agent.domain.ChangeType;
import com.codeguard.agent.domain.ChangedFile;
import com.codeguard.agent.domain.DiffHunk;
import com.codeguard.agent.domain.DiffLine;
import com.codeguard.agent.domain.DiffLineType;
import com.codeguard.agent.domain.FileKind;
import com.codeguard.agent.domain.ParsedDiff;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** GitDiffParser：普通类，负责解析 diff */
@Component
public class GitDiffParser {

    private static final Pattern DIFF_HEADER =
            Pattern.compile("^diff --git a/(.*) b/(.*)$");

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@ ?(.*)$");

    /** 接收原始 diff 字符串，返回 ParsedDiff */
    public ParsedDiff parse(String rawDiff) {
        if (rawDiff == null || rawDiff.isBlank()) {
            return ParsedDiff.of(List.of());
        }

        List<ChangedFile> files = new ArrayList<>();

        /** 解析过程中的临时文件对象 */
        MutableFile currentFile = null;
        /** 解析过程中的临时代码块对象 */
        MutableHunk currentHunk = null;

        int oldLine = 0;
        int newLine = 0;

        String[] lines = rawDiff
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split("\n", -1);

        for (String line : lines) {
            Matcher fileMatcher = DIFF_HEADER.matcher(line);

            if (fileMatcher.matches()) {
                if (currentFile != null) {
                    currentFile.addHunk(currentHunk);
                    files.add(currentFile.toChangedFile());
                }

                currentFile = new MutableFile(fileMatcher.group(1), fileMatcher.group(2));
                currentHunk = null;
                continue;
            }

            if (currentFile == null) {
                continue;
            }

            if (line.startsWith("--- ")) {
                currentFile.oldPath = normalizePathMarker(line.substring(4));
                continue;
            }

            if (line.startsWith("+++ ")) {
                currentFile.newPath = normalizePathMarker(line.substring(4));
                continue;
            }

            if (line.startsWith("new file mode")) {
                currentFile.changeType = ChangeType.ADDED;
                continue;
            }

            if (line.startsWith("deleted file mode")) {
                currentFile.changeType = ChangeType.DELETED;
                continue;
            }

            Matcher hunkMatcher = HUNK_HEADER.matcher(line);

            if (hunkMatcher.matches()) {
                currentFile.addHunk(currentHunk);

                int oldStart = Integer.parseInt(hunkMatcher.group(1));
                int oldCount = countOrDefault(hunkMatcher.group(2));
                int newStart = Integer.parseInt(hunkMatcher.group(3));
                int newCount = countOrDefault(hunkMatcher.group(4));
                String header = hunkMatcher.group(5);

                currentHunk = new MutableHunk(oldStart, oldCount, newStart, newCount, header);

                oldLine = oldStart;
                newLine = newStart;
                continue;
            }

            if (currentHunk == null || line.startsWith("\\ No newline")) {
                continue;
            }

            if (line.startsWith("+")) {
                currentHunk.lines.add(
                        new DiffLine(
                                DiffLineType.ADDITION,
                                null,
                                newLine,
                                line.substring(1)
                        )
                );
                currentFile.additions++;
                newLine++;
            } else if (line.startsWith("-")) {
                currentHunk.lines.add(
                        new DiffLine(
                                DiffLineType.DELETION,
                                oldLine,
                                null,
                                line.substring(1)
                        )
                );
                currentFile.deletions++;
                oldLine++;
            } else {
                String content = line.startsWith(" ") ? line.substring(1) : line;

                currentHunk.lines.add(
                        new DiffLine(
                                DiffLineType.CONTEXT,
                                oldLine,
                                newLine,
                                content
                        )
                );

                oldLine++;
                newLine++;
            }
        }

        if (currentFile != null) {
            currentFile.addHunk(currentHunk);
            files.add(currentFile.toChangedFile());
        }

        return ParsedDiff.of(files);
    }

    private static int countOrDefault(String count) {
        if (count == null || count.isBlank()) {
            return 1;
        }
        return Integer.parseInt(count);
    }

    private static String normalizePathMarker(String marker) {
        if (marker.equals("/dev/null")) {
            return marker;
        }

        if (marker.startsWith("a/") || marker.startsWith("b/")) {
            return marker.substring(2);
        }

        return marker;
    }

    private static final class MutableFile {
        private String oldPath;
        private String newPath;
        private ChangeType changeType = ChangeType.MODIFIED;
        private int additions;
        private int deletions;
        private final List<DiffHunk> hunks = new ArrayList<>();

        private MutableFile(String oldPath, String newPath) {
            this.oldPath = oldPath;
            this.newPath = newPath;
        }

        private void addHunk(MutableHunk hunk) {
            if (hunk != null) {
                hunks.add(hunk.toDiffHunk());
            }
        }

        private ChangedFile toChangedFile() {
            if ("/dev/null".equals(oldPath)) {
                changeType = ChangeType.ADDED;
            }

            if ("/dev/null".equals(newPath)) {
                changeType = ChangeType.DELETED;
            }

            return new ChangedFile(
                    oldPath,
                    newPath,
                    changeType,
                    FileKind.fromPath(newPath),
                    additions,
                    deletions,
                    hunks
            );
        }
    }

    private record MutableHunk(
            int oldStart,
            int oldCount,
            int newStart,
            int newCount,
            String header,
            List<DiffLine> lines
    ) {
        private MutableHunk(
                int oldStart,
                int oldCount,
                int newStart,
                int newCount,
                String header
        ) {
            this(
                    oldStart,
                    oldCount,
                    newStart,
                    newCount,
                    header == null ? "" : header,
                    new ArrayList<>()
            );
        }

        private DiffHunk toDiffHunk() {
            return new DiffHunk(
                    oldStart,
                    oldCount,
                    newStart,
                    newCount,
                    header,
                    lines
            );
        }
    }
}