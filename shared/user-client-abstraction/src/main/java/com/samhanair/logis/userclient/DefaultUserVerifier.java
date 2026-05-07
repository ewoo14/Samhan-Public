package com.samhanair.logis.userclient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link UserVerifier} 기본 구현 — Phase 9 W4 (W3 backlog #1 채택, shared abstraction).
 *
 * <p>RestClient + Caffeine cache (TTL 60s, max 10000) 표준. notification-service / groupware-service
 * / dashboard-service 의 중복 구현을 본 클래스로 일원화.
 *
 * <p>실패 정책 (skeleton 단계 fail-soft) — 네트워크 / discovery 실패 시 검증 통과 (true) 반환.
 * Phase 10 cutover 시점에 {@link UserVerifierProperties#isFailFast()} = true 토글로 strict 모드.
 *
 * <p>회귀 안전성: 기존 {@code UserClient} 의 동일 정책 (404=false / 그 외 4xx/5xx = throw / 네트워크
 * 실패 = fail-soft true) 1:1 보존.
 */
@Slf4j
public class DefaultUserVerifier implements UserVerifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient.Builder builder;
    private final UserVerifierProperties props;
    private final Cache<UUID, Boolean> existsCache;

    public DefaultUserVerifier(RestClient.Builder builder, UserVerifierProperties props) {
        this.builder = builder;
        this.props = props;
        this.existsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(props.getTtlSeconds()))
                .maximumSize(props.getMaxSize())
                .build();
    }

    @Override
    public boolean exists(UUID userId) {
        if (userId == null) {
            return false;
        }
        Boolean cached = existsCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }
        boolean result = callExists(userId);
        existsCache.put(userId, result);
        return result;
    }

    @Override
    public Map<UUID, Boolean> verifyBulk(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Boolean> result = new HashMap<>();
        Set<UUID> missing = new HashSet<>();
        for (UUID id : userIds) {
            if (id == null) {
                continue;
            }
            Boolean cached = existsCache.getIfPresent(id);
            if (cached != null) {
                result.put(id, cached);
            } else {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return result;
        }
        try {
            RestClient client = builder.baseUrl(props.getBaseUrl()).build();
            String response = client.post()
                    .uri("/internal/users/verify-bulk")
                    .header("X-Internal-Token", props.getInternalToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userIds", new ArrayList<>(missing)))
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response == null ? "{}" : response);
            JsonNode data = root.has("data") ? root.path("data") : root;
            JsonNode existsNode = data.path("exists");
            if (existsNode.isObject()) {
                existsNode.fieldNames().forEachRemaining(idStr -> {
                    try {
                        UUID id = UUID.fromString(idStr);
                        boolean ok = existsNode.path(idStr).asBoolean(false);
                        existsCache.put(id, ok);
                        result.put(id, ok);
                    } catch (IllegalArgumentException ignored) {
                        // 응답에 잘못된 UUID — skip
                    }
                });
            }
            for (UUID id : missing) {
                result.putIfAbsent(id, false);
            }
            return result;
        } catch (Exception ex) {
            log.warn("DefaultUserVerifier bulk verify 실패 — size={} msg={} fail-fast={}",
                    missing.size(), ex.getMessage(), props.isFailFast());
            if (props.isFailFast()) {
                for (UUID id : missing) {
                    result.putIfAbsent(id, false);
                }
            } else {
                for (UUID id : missing) {
                    result.putIfAbsent(id, true);
                }
            }
            return result;
        }
    }

    @Override
    public void invalidateCache() {
        existsCache.invalidateAll();
    }

    private boolean callExists(UUID userId) {
        try {
            RestClient client = builder.baseUrl(props.getBaseUrl()).build();
            client.get()
                    .uri("/internal/users/{id}", userId)
                    .header("X-Internal-Token", props.getInternalToken())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            log.warn("DefaultUserVerifier lookup 예외 — userId={}, status={}", userId, ex.getStatusCode());
            throw ex;
        } catch (Exception ex) {
            log.warn("DefaultUserVerifier lookup 실패 — userId={}, msg={} fail-fast={}",
                    userId, ex.getMessage(), props.isFailFast());
            return !props.isFailFast();
        }
    }
}
