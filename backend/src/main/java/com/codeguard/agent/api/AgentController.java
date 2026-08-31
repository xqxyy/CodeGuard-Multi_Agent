package com.codeguard.agent.api;

import com.codeguard.agent.service.AgentCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 目录接口。
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentCatalogService agentCatalogService;

    public AgentController(AgentCatalogService agentCatalogService) {
        this.agentCatalogService = agentCatalogService;
    }

    @GetMapping
    public List<AgentCatalogItem> listAgents() {
        return agentCatalogService.listAgents();
    }
}
