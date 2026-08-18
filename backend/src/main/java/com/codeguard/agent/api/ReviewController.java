package com.codeguard.agent.api;

import com.codeguard.agent.diff.GitDiffParser;
import com.codeguard.agent.domain.DiffSummary;
import com.codeguard.agent.domain.ParsedDiff;
import com.codeguard.agent.domain.ReviewSourceType;
import com.codeguard.agent.sample.SampleDiff;
import com.codeguard.agent.sample.SampleDiffService;
import com.codeguard.agent.service.ReviewAsyncExecutor;
import com.codeguard.agent.service.ReviewWorkflowService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Review 接口控制器。
 *
 * Controller 只负责接收 HTTP 请求、调用 Service、返回结果，不直接写 Agent 业务逻辑。
 */
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final GitDiffParser diffParser;
    private final ReviewWorkflowService reviewWorkflowService;
    private final ReviewAsyncExecutor reviewAsyncExecutor;
    private final SampleDiffService sampleDiffService;

    public ReviewController(
            GitDiffParser diffParser,
            ReviewWorkflowService reviewWorkflowService,
            ReviewAsyncExecutor reviewAsyncExecutor,
            SampleDiffService sampleDiffService
    ) {
        this.diffParser = diffParser;
        this.reviewWorkflowService = reviewWorkflowService;
        this.reviewAsyncExecutor = reviewAsyncExecutor;
        this.sampleDiffService = sampleDiffService;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @PostMapping("/diff/parse")
    public ParseDiffResponse parse(@Valid @RequestBody ReviewRequest request) {
        ParsedDiff parsedDiff = diffParser.parse(request.diffText());
        return new ParseDiffResponse(toDto(parsedDiff.summary()), parsedDiff.files());
    }

    /**
     * 企业默认入口：异步提交 Review。
     */
    @PostMapping("/reviews")
    public ReviewJobResponse submitReview(@Valid @RequestBody ReviewRequest request) {
        ReviewJobResponse job = reviewWorkflowService.submit(request);
        reviewAsyncExecutor.execute(job.reviewId());
        return job;
    }

    /**
     * 同步入口：用于测试、课堂演示和不需要异步轮询的脚本。
     */
    @PostMapping("/reviews/sync")
    public ReviewResponse reviewSync(@Valid @RequestBody ReviewRequest request) {
        return reviewWorkflowService.review(request);
    }

    @GetMapping("/reviews")
    public List<ReviewListItem> reviews() {
        return reviewWorkflowService.listRecent();
    }

    @GetMapping("/reviews/{reviewId}")
    public ReviewResponse review(@PathVariable UUID reviewId) {
        return reviewWorkflowService.get(reviewId);
    }

    @GetMapping("/reviews/{reviewId}/progress")
    public ReviewProgressResponse progress(@PathVariable UUID reviewId) {
        return reviewWorkflowService.progress(reviewId);
    }

    @GetMapping("/reviews/{reviewId}/markdown")
    public MarkdownResponse markdown(@PathVariable UUID reviewId) {
        return new MarkdownResponse(reviewWorkflowService.markdown(reviewId));
    }

    @GetMapping("/samples")
    public List<SampleDiffDto> samples() {
        return sampleDiffService.list()
                .stream()
                .map(this::toSampleDto)
                .toList();
    }

    @GetMapping("/samples/{sampleId}")
    public SampleDiffDto sample(@PathVariable String sampleId) {
        return toSampleDto(sampleDiffService.get(sampleId));
    }

    @PostMapping("/samples/{sampleId}/reviews")
    public ReviewJobResponse submitSampleReview(@PathVariable String sampleId) {
        SampleDiff sample = sampleDiffService.get(sampleId);
        ReviewRequest request = new ReviewRequest(
                sample.title(),
                sample.diffText(),
                "default",
                "sample-library",
                ReviewSourceType.SAMPLE.name(),
                "/api/samples/" + sample.id(),
                null
        );

        ReviewJobResponse job = reviewWorkflowService.submit(request);
        reviewAsyncExecutor.execute(job.reviewId());
        return job;
    }

    @PostMapping("/samples/{sampleId}/reviews/sync")
    public ReviewResponse reviewSampleSync(@PathVariable String sampleId) {
        SampleDiff sample = sampleDiffService.get(sampleId);
        return reviewWorkflowService.review(new ReviewRequest(
                sample.title(),
                sample.diffText(),
                "default",
                "sample-library",
                ReviewSourceType.SAMPLE.name(),
                "/api/samples/" + sample.id(),
                null
        ));
    }

    private SampleDiffDto toSampleDto(SampleDiff sample) {
        return new SampleDiffDto(
                sample.id(),
                sample.title(),
                sample.category(),
                sample.description(),
                sample.expectedTags(),
                sample.diffText()
        );
    }

    private DiffSummaryDto toDto(DiffSummary summary) {
        Map<String, Long> filesByKind = summary.filesByKind()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue));

        return new DiffSummaryDto(
                summary.filesChanged(),
                summary.additions(),
                summary.deletions(),
                filesByKind
        );
    }
}
