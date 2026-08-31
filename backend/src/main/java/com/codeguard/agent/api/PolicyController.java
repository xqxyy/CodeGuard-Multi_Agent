package com.codeguard.agent.api;

import com.codeguard.agent.service.PolicyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 策略中心接口。
 */
@RestController
@RequestMapping("/api")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/policies")
    public List<ReviewPolicyDto> listPolicies() {
        return policyService.listPolicies();
    }

    @GetMapping("/projects/{projectKey}/policy")
    public ReviewPolicyDto getPolicy(@PathVariable String projectKey) {
        return policyService.getPolicy(projectKey);
    }

    @PostMapping("/projects/{projectKey}/policy")
    public ReviewPolicyDto updatePolicy(
            @PathVariable String projectKey,
            @Valid @RequestBody ReviewPolicyRequest request
    ) {
        return policyService.updatePolicy(projectKey, request);
    }
}
