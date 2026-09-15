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
import com.codeguard.agent.config.CodeGuardProperties;
import com.codeguard.agent.diff.GitDiffParser;
import com.codeguard.agent.domain.AgentStatus;
import com.codeguard.agent.domain.AgentTraceRecord;
import com.codeguard.agent.domain.AgentType;
import com.codeguard.agent.domain.ParsedDiff;
import com.codeguard.agent.domain.ReviewContext;
import com.codeguard.agent.domain.ReviewContextSnapshot;
import com.codeguard.agent.domain.ReviewFinding;
import com.codeguard.agent.domain.RouterDecision;
import com.codeguard.agent.persistence.AgentTraceEntity;
import com.codeguard.agent.persistence.AgentTraceRepository;
import com.codeguard.agent.persistence.ReviewEntity;
import com.codeguard.agent.persistence.ReviewIssueEntity;
import com.codeguard.agent.persistence.ReviewIssueRepository;
import com.codeguard.agent.persistence.ReviewRepository;
import com.codeguard.agent.security.CurrentUserService;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
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
            List.of(
                    AgentType.CONTEXT_ENRICHMENT,
                    AgentType.BUG_LOGIC,
                    AgentType.SECURITY,
                    AgentType.CODE_QUALITY,
                    AgentType.TEST_COVERAGE,
                    AgentType.STATIC_ANALYSIS,
                    AgentType.KNOWLEDGE_BASE
            );

    private static final int TOTAL_AGENT_STAGES = 10;

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
    private final PolicyService policyService;
    private final ReviewTaskStateService reviewTaskStateService;
    private final ReviewTrafficGuard reviewTrafficGuard;
    private final ReviewContextEnrichmentService contextEnrichmentService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;
    private final Executor reviewAgentExecutor;
    private final CodeGuardProperties properties;
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
            PolicyService policyService,
            ReviewTaskStateService reviewTaskStateService,
            ReviewTrafficGuard reviewTrafficGuard,
            ReviewContextEnrichmentService contextEnrichmentService,
            CurrentUserService currentUserService,
            AuditLogService auditLogService,
            @Qualifier("reviewAgentExecutor") Executor reviewAgentExecutor,
            CodeGuardProperties properties
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
        this.policyService = policyService;
        this.reviewTaskStateService = reviewTaskStateService;
        this.reviewTrafficGuard = reviewTrafficGuard;
        this.contextEnrichmentService = contextEnrichmentService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
        this.reviewAgentExecutor = reviewAgentExecutor;
        this.properties = properties;
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
        return submit(request, null);
    }

    /**
     * 创建异步 Review 任务，支持可选 Idempotency-Key。
     */
    @Transactional
    public ReviewJobResponse submit(ReviewRequest request, String idempotencyKey) {
        String organizationKey = currentUserService.organizationKey();
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        if (normalizedIdempotencyKey != null) {
            var existing = reviewRepository.findFirstByOrganizationKeyAndIdempotencyKey(
                    organizationKey,
                    normalizedIdempotencyKey
            );
            if (existing.isPresent()) {
                return toJobResponse(existing.get(), true);
            }
        }

        reviewTrafficGuard.assertCanSubmit(organizationKey);
        policyService.validateDiffSize(request.normalizedProjectKey(), request.diffText());
        ParsedDiff parsedDiff = diffParser.parse(request.diffText());
        var effectiveOptions = policyService.resolveOptions(request.normalizedProjectKey(), request.options());

        projectService.ensureRepository(
                request.normalizedProjectKey(),
                request.normalizedRepositoryName(),
                request.normalizedSourceType().toLowerCase(),
                request.sourceUrl()
        );

        ReviewEntity review = ReviewEntity.createQueued(
                organizationKey,
                request,
                parsedDiff,
                effectiveOptions,
                normalizedIdempotencyKey
        );
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            if (normalizedIdempotencyKey == null) {
                throw exception;
            }
            return reviewRepository.findFirstByOrganizationKeyAndIdempotencyKey(
                            organizationKey,
                            normalizedIdempotencyKey
                    )
                    .map(existing -> toJobResponse(existing, true))
                    .orElseThrow(() -> exception);
        }
        auditLogService.record("REVIEW_SUBMITTED", "REVIEW", review.getId().toString(),
                "提交审查任务 " + review.getTitle());

        return toJobResponse(review, false);
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
        ReviewEntity review = reviewTaskStateService.claimQueued(reviewId).orElse(null);
        if (review == null) {
            return;
        }

        try {
            issueRepository.deleteByReviewId(reviewId);
            traceRepository.deleteByReviewId(reviewId);

            ParsedDiff parsedDiff = diffParser.parse(review.getDiffText());
            RouterDecision routerDecision = routerAgent.route(parsedDiff, review.getDiffText());
            ReviewContextSnapshot contextSnapshot = contextEnrichmentService.enrich(
                    parsedDiff,
                    review.getDiffText(),
                    review.getSourceUrl()
            );
            ReviewContext context = new ReviewContext(
                    review.getTitle(),
                    review.getDiffText(),
                    parsedDiff,
                    routerDecision,
                    contextSnapshot,
                    review.getSourceUrl()
            );

            saveTrace(reviewId, routerTrace(routerDecision, parsedDiff));

            RuleAgentRun ruleRun = runRuleAgents(review, context, routerDecision);
            List<ReviewFinding> ruleFindings = ruleRun.findings();
            LlmReviewAgent.LlmReviewRun llmRun = runLlmAgent(review, context, ruleFindings);

            List<ReviewFinding> allFindings = new ArrayList<>();
            allFindings.addAll(ruleFindings);
            allFindings.addAll(llmRun.findings());

            List<ReviewFinding> sortedFindings = allFindings.stream()
                    .sorted(Comparator.comparing(ReviewFinding::severity).thenComparing(ReviewFinding::tag))
                    .toList();

            SummaryRun summaryRun = runSummaryAgent(sortedFindings, ruleRun.hasRequiredFailure());
            saveTrace(reviewId, summaryRun.trace());

            ReviewEntity completedReview = findReview(reviewId);
            if (ruleRun.hasRequiredFailure()) {
                completedReview.completePartial(
                        summaryRun.summary().markdown(),
                        summaryRun.summary().recommendation(),
                        summaryRun.summary().riskScore(),
                        "必要审查 Agent 执行失败：" + ruleRun.failedAgents()
                );
            } else {
                completedReview.complete(
                        summaryRun.summary().markdown(),
                        summaryRun.summary().recommendation(),
                        summaryRun.summary().riskScore()
                );
            }
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
            auditLogService.recordSystem(
                    completedReview.getOrganizationKey(),
                    "REVIEW_COMPLETED",
                    "REVIEW",
                    completedReview.getId().toString(),
                    "审查完成，风险分 " + completedReview.getRiskScore()
            );
        } catch (Exception exception) {
            ReviewEntity failedReview = findReview(reviewId);
            failedReview.fail(exception.getMessage());
            reviewRepository.save(failedReview);
            auditLogService.recordSystem(
                    failedReview.getOrganizationKey(),
                    "REVIEW_FAILED",
                    "REVIEW",
                    failedReview.getId().toString(),
                    "审查失败：" + exception.getMessage()
            );
        }
    }

    @Transactional(readOnly = true)
    public List<ReviewListItem> listRecent() {
        return reviewRepository.findTop20ByOrganizationKeyOrderByCreatedAtDesc(currentUserService.organizationKey())
                .stream()
                .map(mapper::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReviewListItem> listRecent(String projectKey) {
        return reviewRepository
                .findTop20ByOrganizationKeyAndProjectKeyOrderByCreatedAtDesc(
                        currentUserService.organizationKey(),
                        projectKey
                )
                .stream()
                .map(mapper::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewResponse get(UUID reviewId) {
        ReviewEntity review = findReview(reviewId);
        assertCurrentOrganization(review);
        List<ReviewIssueEntity> issues = issueRepository.findByReviewIdOrderByCreatedAtAsc(reviewId);
        List<AgentTraceEntity> traces = traceRepository.findByReviewIdOrderByStartedAtAsc(reviewId);
        ParsedDiff parsedDiff = diffParser.parse(review.getDiffText());
        return mapper.toResponse(review, issues, traces, parsedDiff.summary());
    }

    @Transactional(readOnly = true)
    public ReviewProgressResponse progress(UUID reviewId) {
        ReviewEntity review = findReview(reviewId);
        assertCurrentOrganization(review);
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
        ReviewEntity review = findReview(reviewId);
        assertCurrentOrganization(review);
        return review.getMarkdown();
    }

    private RuleAgentRun runRuleAgents(
            ReviewEntity review,
            ReviewContext context,
            RouterDecision routerDecision
    ) {
        List<PendingAgent> futures = new ArrayList<>();
        List<AgentTraceRecord> skippedTraces = new ArrayList<>();

        for (AgentType agentType : RULE_AGENT_ORDER) {
            if (!isAgentEnabled(review, agentType)) {
                skippedTraces.add(skippedTrace(agentType, "本次审查选项关闭了该 Agent"));
            } else if (!routerDecision.shouldRun(agentType)) {
                skippedTraces.add(skippedTrace(agentType, routerDecision.reasonFor(agentType)));
            } else {
                futures.add(new PendingAgent(
                        agentType,
                        CompletableFuture.supplyAsync(() -> runAgent(agentType, context), reviewAgentExecutor)
                ));
            }
        }

        skippedTraces.forEach(trace -> saveTrace(review.getId(), trace));

        List<ReviewFinding> findings = new ArrayList<>();
        List<AgentType> failedAgents = new ArrayList<>();
        for (PendingAgent pending : futures) {
            AgentRun run;
            try {
                run = pending.future().get(properties.review().agentExecutionTimeoutSeconds(), TimeUnit.SECONDS);
            } catch (Exception exception) {
                pending.future().cancel(true);
                Instant now = Instant.now();
                run = new AgentRun(
                        List.of(),
                        new AgentTraceRecord(
                                pending.agentType(),
                                AgentStatus.FAILED,
                                "Agent execution exceeded its deadline",
                                "Agent execution did not complete",
                                null,
                                properties.review().agentExecutionTimeoutSeconds() * 1000L,
                                now,
                                now,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                exception.getClass().getSimpleName()
                        )
                );
            }
            findings.addAll(run.findings());
            saveTrace(review.getId(), run.trace());
            if (run.trace().status() == AgentStatus.FAILED) {
                failedAgents.add(run.trace().agentType());
            }
        }

        return new RuleAgentRun(findings, failedAgents);
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

    private SummaryRun runSummaryAgent(List<ReviewFinding> findings, boolean incomplete) {
        Instant startedAt = Instant.now();
        SummaryAgent.SummaryResult summary = summaryAgent.summarize(findings, incomplete);
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
            case CONTEXT_ENRICHMENT, STATIC_ANALYSIS, KNOWLEDGE_BASE -> true;
            default -> true;
        };
    }

    private void saveTrace(UUID reviewId, AgentTraceRecord trace) {
        ReviewEntity review = findReview(reviewId);
        traceRepository.save(AgentTraceEntity.from(review, trace));
    }

    private ReviewEntity findReview(UUID reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Review not found: " + reviewId));
    }

    private ReviewJobResponse toJobResponse(ReviewEntity review, boolean replayed) {
        return new ReviewJobResponse(
                review.getId(),
                review.getStatus(),
                "/api/reviews/" + review.getId() + "/progress",
                "/api/reviews/" + review.getId(),
                replayed
        );
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String stripped = value.strip();
        if (stripped.length() > 120) {
            throw new IllegalArgumentException("Idempotency-Key must be at most 120 characters");
        }
        return stripped;
    }

    private void assertCurrentOrganization(ReviewEntity review) {
        if (!review.getOrganizationKey().equals(currentUserService.organizationKey())) {
            throw new EntityNotFoundException("Review not found: " + review.getId());
        }
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

    private record PendingAgent(AgentType agentType, CompletableFuture<AgentRun> future) {}

    private record RuleAgentRun(List<ReviewFinding> findings, List<AgentType> failedAgents) {
        private RuleAgentRun {
            findings = List.copyOf(findings);
            failedAgents = List.copyOf(failedAgents);
        }

        boolean hasRequiredFailure() {
            return !failedAgents.isEmpty();
        }
    }

    private record SummaryRun(SummaryAgent.SummaryResult summary, AgentTraceRecord trace) {}
}
