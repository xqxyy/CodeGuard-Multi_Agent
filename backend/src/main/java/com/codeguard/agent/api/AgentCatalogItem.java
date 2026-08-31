package com.codeguard.agent.api;

import com.codeguard.agent.domain.AgentType;
import java.util.List;

public record AgentCatalogItem(
        AgentType type,
        String name,
        String description,
        String engine,
        String status,
        boolean llmBacked,
        List<String> signals,
        String enterpriseValue
) {}
