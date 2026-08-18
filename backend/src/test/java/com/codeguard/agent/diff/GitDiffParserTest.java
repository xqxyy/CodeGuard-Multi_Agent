package com.codeguard.agent.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeguard.agent.domain.DiffLineType;
import com.codeguard.agent.domain.FileKind;
import com.codeguard.agent.domain.ParsedDiff;
import org.junit.jupiter.api.Test;

/**
 * Git diff 解析器测试
 *
 * 重点验证原始 diff 文本能被解析成文件、代码块和新增/删除行
 */
class GitDiffParserTest {

  private final GitDiffParser parser = new GitDiffParser();

  /**
   * 验证 Java 文件 diff 可以解析出文件类型、增删行数量和新增行内容
   */
  @Test
  void parsesJavaDiffIntoStructuredObjects() {
    String diff =
        """
        diff --git a/src/main/java/UserService.java b/src/main/java/UserService.java
        --- a/src/main/java/UserService.java
        +++ b/src/main/java/UserService.java
        @@ -1,3 +1,3 @@
         public String name() {
        -  return "Tom";
        +  return null;
         }
        """;

    ParsedDiff parsedDiff = parser.parse(diff);

    assertThat(parsedDiff.files()).hasSize(1);
    assertThat(parsedDiff.summary().additions()).isEqualTo(1);
    assertThat(parsedDiff.summary().deletions()).isEqualTo(1);
    assertThat(parsedDiff.files().getFirst().fileKind()).isEqualTo(FileKind.JAVA);
    assertThat(parsedDiff.files().getFirst().hunks().getFirst().lines())
        .anySatisfy(
            line -> {
              assertThat(line.type()).isEqualTo(DiffLineType.ADDITION);
              assertThat(line.content()).contains("return null");
            });
  }
}
