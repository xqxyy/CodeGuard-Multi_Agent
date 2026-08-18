package com.codeguard.agent.service;

import com.codeguard.agent.agent.AgentExecutionResult;
import com.codeguard.agent.agent.LlmReviewAgent;
import com.codeguard.agent.agent.ReviewAgent;
import com.codeguard.agent.agent.RouterAgent;
import com.codeguard.agent.agent.SummaryAgent;
import com.codeguard.agent.api.AgentTraceDto;
import com.codeguard.agent.api.ReviewJobResponse;
import com.codeguard.agent.api.ReviewListItem;
import com.codeguard.agent.api.ReviewProgressResponse;
import com.codeguard.agent.api.ReviewRequest;
import com.codeguard.agent.api.ReviewResponse;
import com.codeguard.agent.diff.GitDiffParser;
import com.codeguard.agent.domain.AgentStatus;
import com.codeguard.agent.domain.AgentTraceRecord;
import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ParsedDiff;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.RouterDecision;
import com.codeguard.agent.persistence.AgentTraceEntity;
import com.codeguard.agent.persistence.AgentTraceRepository;
import com.codeguard.agent.persistence.ReviewEntity;
import com.codeguard.agent.persistence.ReviewIssueEntity;
import com.codeguard.agent.persistence.ReviewIssueRepository;
import com.codeguard.agent.persistence.ReviewRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Review 核心编排服务。
 *
 * 企业版思路：
 * 1. 先创建 Review 任务，立即返回 reviewId。
 * 2. 后台异步执行 Router、规则 Agent、LLM Agent、Summary。
 * 3. 每个 Agent 执行结果都保存 Trace，方便前端展示进度和审计。
 */
@Service
public class ReviewWorkflowService {

    private static final List<AgentType> RULE_AGENT_ORDER =
            List.of(AgentType.BUG_LOGIC, AgentType.SECURITY, AgentType.CODE_QUALITY, AgentType.TEST_COVERAGE);

    private static final int TOTAL_AGENT_STAGES = 7;

    private final GitDiffParser diffParser;
    private final RouterAgent routerAgent;
    private final LlmReviewAgent llmReviewAgent;
    private final SummaryAgent summaryAgent;
    private final ReviewRepository reviewRepository;
    private final ReviewIssueRepository issueRepository;
    private final AgentTraceRepository traceRepository;
    private final ReviewMapper mapper;
    private final ReviewRuntimeCache runtimeCache;
    private final ProjectService projectService;
    private final Executor reviewTaskExecutor;
    private final Map<AgentType, ReviewAgent> agents;

    public ReviewWorkflowService(
            GitDiffParser diffParser,
            RouterAgent routerAgent,
            LlmReviewAgent llmReviewAgent,
            SummaryAgent summaryAgent,
            List<ReviewAgent> agents,
            ReviewRepository reviewRepository,
            ReviewIssueRepository issueRepository,
            AgentTraceRepository traceRepository,
            ReviewMapper mapper,
            ReviewRuntimeCache runtimeCache,
            ProjectService projectService,
            @Qualifier("reviewTaskExecutor") Executor reviewTaskExecutor
    ) {
        this.diffParser = diffParser;
        this.routerAgent = routerAgent;
        this.llmReviewAgent = llmReviewAgent;
        this.summaryAgent = summaryAgent;
        this.reviewRepository = reviewRepository;
        this.issueRepository = issueRepository;
        this.traceRepository = traceRepository;
        this.mapper = mapper;
        this.runtimeCache = runtimeCache;
        this.projectService = projectService;
        this.reviewTaskExecutor = reviewTaskExecutor;
        this.agents = agents.stream()
                .collect(Collectors.toMap(
                        ReviewAgent::type,
                        Function.identity(),
                        (left, right) -> left,
                        () -> new EnumMap<>(AgentType.class)
                ));
    }

    /**
     * 创建异步 Review 任务，只负责入库，不在 HTTP 线程里执行 Agent。
     */
    @Transactional
    public ReviewJobResponse submit(ReviewRequest request) {
        ParsedDiff parsedDiff = diffParser.parse(request.diffText());

        projectService.ensureRepository(
                request.normalizedProjectKey(),
                request.normalizedRepositoryName(),
                request.normalizedSourceType().toLowerCase(),
                request.sourceUrl()
        );

        ReviewEntity review = ReviewEntity.createQueued(request, parsedDiff);
        reviewRepository.save(review);

        return new ReviewJobResponse(
                review.getId(),
                review.getStatus(),
                "/api/reviews/" + review.getId() + "/progress",
                "/api/reviews/" + review.getId()
        );
    }

    /**
     * 同步执行 Review，主要用于测试、命令行验证和保留旧接口兼容。
     */
    public ReviewResponse review(ReviewRequest request) {
        ReviewJobResponse job = submit(request);
        execute(job.reviewId());
        return get(job.reviewId());
    }

    /**
     * 后台任务实际执行入口。
     */
    public void execute(UUID reviewId) {
        ReviewEntity review = findReview(reviewId);

        try {
            markRunning(review);
            issueRepository.deleteByReviewId(reviewId);
            traceRepository.deleteByReviewId(reviewId);

            ParsedDiff parsedDiff = diffParser.parse(review.getDiffText());
            RouterDecision routerDecision = routerAgent.route(parsedDiff, review.getDiffText());
            ReviewContext context = new ReviewContext(review.getTitle(), review.getDiffText(), parsedDiff, routerDecision);

            saveTrace(reviewId, routerTrace(routerDecision, parsedDiff));

            List<ReviewFinding> ruleFindings = runRuleAgents(review, context, routerDecision);
            LlmReviewAgent.LlmReviewRun llmRun = runLlmAgent(review, context, ruleFindings);

            List<ReviewFinding> allFindings = new ArrayList<>();
            allFindings.addAll(ruleFindings);
            allFindings.addAll(llmRun.findings());

            List<ReviewFinding> sortedFindings = allFindings.stream()
                    .sorted(Comparator.comparing(ReviewFinding::severity).thenComparing(ReviewFinding::tag))
                    .toList();

            SummaryRun summaryRun = runSummaryAgent(sortedFindings);
            saveTrace(reviewId, summaryRun.trace());

            ReviewEntity completedReview = findReview(reviewId);
            completedReview.complete(
                    summaryRun.summary().markdown(),
                    summaryRun.summary().recommendation(),
                    summaryRun.summary().riskScore()
            );
            reviewRepository.save(completedReview);

            List<ReviewIssueEntity> issueEntities = sortedFindings.stream()
                    .map(finding -> ReviewIssueEntity.from(completedReview, finding))
                    .toList();
            issueRepository.saveAll(issueEntities);

            runtimeCache.cacheReviewResult(
                    completedReview.getId(),
                    summaryRun.summary().recommendation(),
                    summaryRun.summary().riskScore()
            );
        } catch (Exception exception) {
            ReviewEntity failedReview = findReview(reviewId);
            failedReview.fail(exception.getMessage());
            reviewRepository.save(failedReview);
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewListItem> listRecent() {
        return reviewRepository.findTop20ByOrderByCreatedAtDesc()
                .stream()
                .map(mapper::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewListItem> listRecent(String projectKey) {
        return reviewRepository.findTop20ByProjectKeyOrderByCreatedAtDesc(projectKey)
                .stream()
                .map(mapper::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewResponse get(UUID reviewId) {
        ReviewEntity review = findReview(reviewId);
        List<ReviewIssueEntity> issues = issueRepository.findByReviewIdOrderByCreatedAtAsc(reviewId);
        List<AgentTraceEntity> traces = traceRepository.findByReviewIdOrderByStartedAtAsc(reviewId);
        ParsedDiff parsedDiff = diffParser.parse(review.getDiffText());
        return mapper.toResponse(review, issues, traces, parsedDiff.summary());
    }

    @Transactional(readOnly = true)
    public ReviewProgressResponse progress(UUID reviewId) {
        ReviewEntity review = findReview(reviewId);
        List<AgentTraceEntity> traces = traceRepository.findByReviewIdOrderByStartedAtAsc(reviewId);
        List<AgentTraceDto> traceDtos = traces.stream().map(this::toProgressTrace).toList();

        int completed = (int) traces.stream().filter(trace -> trace.getStatus() == AgentStatus.COMPLETED).count();
        int failed = (int) traces.stream().filter(trace -> trace.getStatus() == AgentStatus.FAILED).count();
        int skipped = (int) traces.stream().filter(trace -> trace.getStatus() == AgentStatus.SKIPPED).count();

        return new ReviewProgressResponse(
                review.getId(),
                review.getTitle(),
                review.getStatus(),
                TOTAL_AGENT_STAGES,
                completed,
                failed,
                skipped,
                review.getRecommendation(),
                review.getRiskScore(),
                traceDtos,
                review.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public String markdown(UUID reviewId) {
        return findReview(reviewId).getMarkdown();
    }

    private List<ReviewFinding> runRuleAgents(
            ReviewEntity review,
            ReviewContext context,
            RouterDecision routerDecision
    ) {
        List<CompletableFuture<AgentRun>> futures = new ArrayList<>();
        List<AgentTraceRecord> skippedTraces = new ArrayList<>();

        for (AgentType agentType : RULE_AGENT_ORDER) {
            if (!isAgentEnabled(review, agentType)) {
                skippedTraces.add(skippedTrace(agentType, "本次审查选项关闭了该 Agent"));
            } else if (!routerDecision.shouldRun(agentType)) {
                skippedTraces.add(skippedTrace(agentType, routerDecision.reasonFor(agentType)));
            } else {
                futures.add(CompletableFuture.supplyAsync(() -> runAgent(agentType, context), reviewTaskExecutor));
            }
        }

        skippedTraces.forEach(trace -> saveTrace(review.getId(), trace));

        List<ReviewFinding> findings = new ArrayList<>();
        for (CompletableFuture<AgentRun> future : futures) {
            AgentRun run = future.join();
            findings.addAll(run.findings());
            saveTrace(review.getId(), run.trace());
        }

        return findings;
    }

    private LlmReviewAgent.LlmReviewRun runLlmAgent(
            ReviewEntity review,
            ReviewContext context,
            List<ReviewFinding> ruleFindings
    ) {
        if (!review.isEnableLlmReview()) {
            AgentTraceRecord trace = skippedTrace(AgentType.LLM_REVIEW, "本次审查选项关闭了 LLM Agent");
            saveTrace(review.getId(), trace);
            return new LlmReviewAgent.LlmReviewRun(List.of(), trace);
        }

        LlmReviewAgent.LlmReviewRun llmRun = llmReviewAgent.review(context, ruleFindings);
        saveTrace(review.getId(), llmRun.trace());
        return llmRun;
    }

    private SummaryRun runSummaryAgent(List<ReviewFinding> findings) {
        Instant startedAt = Instant.now();
        SummaryAgent.SummaryResult summary = summaryAgent.summarize(findings);
        Instant endedAt = Instant.now();

        AgentTraceRecord trace = new AgentTraceRecord(
                AgentType.SUMMARY,
                AgentStatus.COMPLETED,
                "findings=" + findings.size(),
                "recommendation=" + summary.recommendation() + ", riskScore=" + summary.riskScore(),
                null,
                Duration.between(startedAt, endedAt).toMillis(),
                startedAt,
                endedAt
        );

        return new SummaryRun(summary, trace);
    }

    private AgentRun runAgent(AgentType agentType, ReviewContext context) {
        ReviewAgent agent = agents.get(agentType);

        if (agent == null) {
            return new AgentRun(List.of(), skippedTrace(agentType, "Agent 没有注册到 Spring 容器"));
        }

        Instant startedAt = Instant.now();
        try {
            AgentExecutionResult result = agent.review(context);
            Instant endedAt = Instant.now();
            AgentTraceRecord trace = new AgentTraceRecord(
                    agentType,
                    AgentStatus.COMPLETED,
                    "files=" + context.parsedDiff().summary().filesChanged(),
                    result.outputSummary(),
                    null,
                    Duration.between(startedAt, endedAt).toMillis(),
                    startedAt,
                    endedAt
            );
            return new AgentRun(result.findings(), trace);
        } catch (RuntimeException exception) {
            Instant endedAt = Instant.now();
            AgentTraceRecord trace = new AgentTraceRecord(
                    agentType,
                    AgentStatus.FAILED,
                    "files=" + context.parsedDiff().summary().filesChanged(),
                    "Agent 执行失败：" + exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    Duration.between(startedAt, endedAt).toMillis(),
                    startedAt,
                    endedAt,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    exception.getMessage()
            );
            return new AgentRun(List.of(), trace);
        }
    }

    private AgentTraceRecord routerTrace(RouterDecision routerDecision, ParsedDiff parsedDiff) {
        Instant now = Instant.now();
        return new AgentTraceRecord(
                AgentType.ROUTER,
                AgentStatus.COMPLETED,
                "files=" + parsedDiff.summary().filesChanged(),
                "enabledAgents=" + routerDecision.enabledAgents(),
                null,
                0,
                now,
                now
        );
    }

    private AgentTraceRecord skippedTrace(AgentType agentType, String reason) {
        Instant now = Instant.now();
        return new AgentTraceRecord(
                agentType,
                AgentStatus.SKIPPED,
                "Router selected skip path",
                null,
                reason,
                0,
                now,
                now
        );
    }

    private boolean isAgentEnabled(ReviewEntity review, AgentType agentType) {
        return switch (agentType) {
            case BUG_LOGIC -> review.isEnableBugLogic();
            case SECURITY -> review.isEnableSecurity();
            case CODE_QUALITY -> review.isEnableCodeQuality();
            case TEST_COVERAGE -> review.isEnableTestCoverage();
            case LLM_REVIEW -> review.isEnableLlmReview();
            default -> true;
        };
    }

    private void markRunning(ReviewEntity review) {
        review.start();
        reviewRepository.save(review);
    }

    private void saveTrace(UUID reviewId, AgentTraceRecord trace) {
        ReviewEntity review = findReview(reviewId);
        traceRepository.save(AgentTraceEntity.from(review, trace));
    }

    private ReviewEntity findReview(UUID reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + reviewId));
    }

    private AgentTraceDto toProgressTrace(AgentTraceEntity trace) {
        return new AgentTraceDto(
                trace.getId(),
                trace.getAgentType(),
                trace.getStatus(),
                trace.getInputSummary(),
                trace.getOutputSummary(),
                trace.getSkipReason(),
                trace.getDurationMs(),
                trace.getStartedAt(),
                trace.getEndedAt(),
                trace.getPrompt(),
                trace.getRawOutput(),
                trace.getModelName(),
                trace.getProvider(),
                trace.getPromptTokens(),
                trace.getCompletionTokens(),
                trace.getErrorMessage()
        );
    }

    private record AgentRun(List<ReviewFinding> findings, AgentTraceRecord trace) {}

    private record SummaryRun(SummaryAgent.SummaryResult summary, AgentTraceRecord trace) {}
}
