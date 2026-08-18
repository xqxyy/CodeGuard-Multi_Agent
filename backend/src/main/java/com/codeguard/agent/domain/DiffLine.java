package com.codeguard.agent.domain;

/** diff 里的某一行
 * type           这一行是新增、删除、还是上下文
 * oldLineNumber 旧文件里的行号
 * newLineNumber 新文件里的行号
 * content        这一行的代码内容
 * */
public record DiffLine(
        DiffLineType type,
        Integer oldLineNumber,
        Integer newLineNumber,
        String content
) {}