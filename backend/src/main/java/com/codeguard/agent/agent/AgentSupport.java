/** Agent 公共工具类
 *
 * 遍历新增行
 * 判断文件路径
 * 构造 ReviewFinding
 * 截取证据
 */
package com.codeguard.agent.agent;

import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ChangedFile;
import com.codeguard.agent.domain.DiffLineType;
import com.codeguard.agent.domain.FileKind;
import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.ParsedDiff;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 这个类不希望被继承 */
final class AgentSupport {

    private AgentSupport() {}

    static List<LineRef> addedLines(ParsedDiff diff) {
        List<LineRef> refs = new ArrayList<>();

        for (ChangedFile file : diff.files()) {
            file.hunks().forEach(hunk ->
                    hunk.lines().stream()
                            .filter(line -> line.type() == DiffLineType.ADDITION)
                            .forEach(line -> refs.add(
                                    new LineRef(
                                            file.displayPath(),
                                            line.newLineNumber(),
                                            line.content(),
                                            file.fileKind()
                                    )
                            ))
            );
        }

        return refs;
    }

    static boolean fileContains(ChangedFile file, String keyword) {
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);

        return file.hunks().stream()
                .flatMap(hunk -> hunk.lines().stream())
                .anyMatch(line -> lower(line.content()).contains(lowerKeyword));
    }

    static boolean pathContains(ChangedFile file, String keyword) {
        return lower(file.displayPath()).contains(keyword.toLowerCase(Locale.ROOT));
    }

    static boolean rawContains(String rawDiff, String keyword) {
        return lower(rawDiff).contains(keyword.toLowerCase(Locale.ROOT));
    }

    static boolean matches(String content, Pattern pattern) {
        return pattern.matcher(content).find();
    }

    static ReviewFinding finding(
            AgentType agentType,
            IssueTag tag,
            Severity severity,
            String filePath,
            Integer lineNumber,
            String title,
            String detail,
            String suggestion,
            String evidence
    ) {
        return new ReviewFinding(
                agentType,
                tag,
                severity,
                filePath,
                lineNumber,
                title,
                detail,
                suggestion,
                evidence
        );
    }

    static String shortEvidence(String content) {
        if (content == null) {
            return "";
        }

        String trimmed = content.strip();
        if (trimmed.length() > 220) {
            return trimmed.substring(0, 220) + "...";
        }

        return trimmed;
    }

    static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    record LineRef(
            String filePath,
            Integer lineNumber,
            String content,
            FileKind fileKind
    ) {}
}