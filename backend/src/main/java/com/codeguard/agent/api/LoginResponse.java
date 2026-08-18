package com.codeguard.agent.api;

public record LoginResponse(
        String token,
        String tokenType,
        String username,
        String displayName,
        String role
) {}
