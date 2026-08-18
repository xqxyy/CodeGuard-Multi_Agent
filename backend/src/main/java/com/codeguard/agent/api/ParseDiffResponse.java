/** /api/diff/parse 接口的返回结果 */
package com.codeguard.agent.api;

import com.codeguard.agent.domain.ChangedFile;
import java.util.List;

public record ParseDiffResponse(
        DiffSummaryDto summary,
        List<ChangedFile> files
) {}