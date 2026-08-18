/** 汇总智能体
 * 接收所有 Agent 发现的问题
 * 计算风险分
 * 判断是否建议合并
 * 生成一段 Markdown 报告
 */
package com.codeguard.agent.agent;

import com.codeguard.agent.domain.IssueTag;
import com.codeguard.agent.domain.MergeRecommendation;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.Severity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SummaryAgent {

    /** 定义汇总方法。输入是问题列表，输出是汇总结果 */
    public SummaryResult summarize(List<ReviewFinding> findings) {
        /** 遍历所有问题，把每条问题的严重级别分数加起来 */
        int riskScore = Math.min(
                100,
                findings.stream()
                        .mapToInt(finding -> finding.severity().weight())
                        .sum()
        );

        /** 根据问题严重程度生成合并建议 */
        MergeRecommendation recommendation = recommendation(findings);
        String markdown = markdown(findings, recommendation, riskScore);

        return new SummaryResult(markdown, recommendation, riskScore);
    }

    /** recommendation(...) 方法的逻辑是：
     * 有 P0 -> BLOCK
     * 有 P1 或安全问题 -> REQUEST_CHANGES
     * 有普通问题 -> CAN_MERGE_WITH_NOTES
     * 没有问题 -> APPROVE
     */
    private MergeRecommendation recommendation(List<ReviewFinding> findings) {
        boolean hasP0 = findings.stream()
                .anyMatch(finding -> finding.severity() == Severity.P0);

        boolean hasP1 = findings.stream()
                .anyMatch(finding -> finding.severity() == Severity.P1);

        boolean hasSecurity = findings.stream()
                .anyMatch(finding -> finding.tag() == IssueTag.SECURITY);

        if (hasP0) {
            return MergeRecommendation.BLOCK;
        }

        if (hasP1 || hasSecurity) {
            return MergeRecommendation.REQUEST_CHANGES;
        }

        if (!findings.isEmpty()) {
            return MergeRecommendation.CAN_MERGE_WITH_NOTES;
        }

        return MergeRecommendation.APPROVE;
    }

    private String markdown(
            List<ReviewFinding> findings,
            MergeRecommendation recommendation,
            int riskScore
    ) {
        StringBuilder builder = new StringBuilder();

        builder.append("# CodeGuard Review Report\n\n");
        builder.append("## 合并建议\n\n");
        builder.append("- 建议：").append(recommendation).append("\n");
        builder.append("- 风险分：").append(riskScore).append("/100\n\n");

        builder.append("## 问题列表\n\n");

        if (findings.isEmpty()) {
            builder.append("- 暂未发现明显问题\n");
            return builder.toString();
        }

        for (ReviewFinding finding : findings) {
            builder.append("- [")
                    .append(finding.severity())
                    .append("][")
                    .append(finding.tag())
                    .append("] ")
                    .append(finding.title())
                    .append("\n");
        }

        return builder.toString();
    }

    public record SummaryResult(
            String markdown,
            MergeRecommendation recommendation,
            int riskScore
    ) {}
}