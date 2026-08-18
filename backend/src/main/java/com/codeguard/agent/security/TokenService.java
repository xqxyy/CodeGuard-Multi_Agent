package com.codeguard.agent.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 简化版 Bearer Token 服务。
 *
 * 这里实现的是 JWT 风格的 HMAC-SHA256 token，不依赖额外库，便于学习和演示。
 */
@Service
public class TokenService {

    private final ObjectMapper objectMapper;
    private final String secret;
    private final long ttlMinutes;

    public TokenService(
            ObjectMapper objectMapper,
            @Value("${codeguard.security.token-secret}") String secret,
            @Value("${codeguard.security.token-ttl-minutes}") long ttlMinutes
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.ttlMinutes = ttlMinutes;
    }

    public String createToken(DemoUser user) {
        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            String payload = encodeJson(Map.of(
                    "sub", user.username(),
                    "name", user.displayName(),
                    "role", user.role(),
                    "exp", Instant.now().plusSeconds(ttlMinutes * 60).getEpochSecond()
            ));
            String unsigned = header + "." + payload;
            return unsigned + "." + sign(unsigned);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create token", exception);
        }
    }

    public TokenPrincipal verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid token format");
            }

            String unsigned = parts[0] + "." + parts[1];
            if (!sign(unsigned).equals(parts[2])) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            long exp = payload.path("exp").asLong(0);
            if (Instant.now().getEpochSecond() > exp) {
                throw new IllegalArgumentException("Token expired");
            }

            return new TokenPrincipal(
                    payload.path("sub").asText(),
                    payload.path("name").asText(),
                    payload.path("role").asText()
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid token", exception);
        }
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    public record TokenPrincipal(String username, String displayName, String role) {}
}
