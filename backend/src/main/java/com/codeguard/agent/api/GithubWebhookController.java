package com.codeguard.agent.api;

import com.codeguard.agent.service.GithubPrService;
import com.codeguard.agent.service.ReviewAsyncExecutor;
import com.codeguard.agent.service.ReviewWorkflowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GitHub 事件入口。GitHub 不能携带平台 Bearer Token，因此这里强制使用
 * X-Hub-Signature-256 校验 payload，验证完成后才投递异步审查任务。
 */
@RestController
@RequestMapping("/api/integrations/github")
public class GithubWebhookController {

    private final GithubPrService githubPrService;
    private final ReviewWorkflowService reviewWorkflowService;
    private final ReviewAsyncExecutor reviewAsyncExecutor;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public GithubWebhookController(
            GithubPrService githubPrService,
            ReviewWorkflowService reviewWorkflowService,
            ReviewAsyncExecutor reviewAsyncExecutor,
            ObjectMapper objectMapper,
            @Value("${codeguard.github.webhook-secret:}") String webhookSecret
    ) {
        this.githubPrService = githubPrService;
        this.reviewWorkflowService = reviewWorkflowService;
        this.reviewAsyncExecutor = reviewAsyncExecutor;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String payload
    ) throws Exception {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!isValidSignature(payload, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"pull_request".equals(event)) {
            return ResponseEntity.accepted().build();
        }

        JsonNode root = objectMapper.readTree(payload);
        String action = root.path("action").asText();
        if (!"opened".equals(action) && !"reopened".equals(action) && !"synchronize".equals(action)) {
            return ResponseEntity.accepted().build();
        }

        String repository = root.path("repository").path("full_name").asText();
        int pullNumber = root.path("number").asInt();
        if (!repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+") || pullNumber <= 0) {
            return ResponseEntity.badRequest().build();
        }

        ReviewJobResponse job = reviewWorkflowService.submit(
                githubPrService.toReviewRequest(repository, pullNumber, "github-webhook", null),
                deliveryId == null || deliveryId.isBlank() ? null : "github-delivery-" + deliveryId
        );
        if (!job.replayed()) {
            reviewAsyncExecutor.execute(job.reviewId());
        }
        return ResponseEntity.accepted().build();
    }

    private boolean isValidSignature(String payload, String signature) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    signature.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (Exception exception) {
            return false;
        }
    }

    private static String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte current : value) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }
}
