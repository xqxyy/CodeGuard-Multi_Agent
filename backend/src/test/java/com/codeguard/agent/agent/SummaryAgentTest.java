package com.codeguard.agent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeguard.agent.domain.MergeRecommendation;
import java.util.List;
import org.junit.jupiter.api.Test;

class SummaryAgentTest {

    @Test
    void blocksMergeWhenRequiredChecksAreIncompleteEvenWithoutFindings() {
        SummaryAgent.SummaryResult result = new SummaryAgent().summarize(List.of(), true);

        assertThat(result.recommendation()).isEqualTo(MergeRecommendation.BLOCK);
        assertThat(result.markdown()).contains("必要检查未完整执行");
    }
}
