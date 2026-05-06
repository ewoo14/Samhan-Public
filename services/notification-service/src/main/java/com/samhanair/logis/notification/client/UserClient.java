package com.samhanair.logis.notification.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.samhanair.logis.discovery.ServiceDiscoveryClient;
import com.samhanair.logis.notification.config.UserCacheProperties;
import jakarta.annotation.PostConstruct;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * user-service 호출 client — 수신자 정보 lookup ({@code GET /internal/users/{userId}}) +
 * bulk verify ({@code POST /internal/users/verify-bulk}).
 *
 * <p>Phase 9 W3 — BE backlog #4 채택 (groupware-service ApprovalLine N 결재자 fan-out 직렬 RPC
 * 비용). bulk verify endpoint + Caffeine cache (TTL 60초) 로 짧은 시간 반복 lookup 시 RPC 회피.
 *
 * <p>ServiceDiscoveryClient 세 번째 소비자 (W1 partner / W2 groupware → W3 notification).
 *
 * <p>IT 에서는 {@code @MockBean UserClient} 격리 의무 (memory feedback_it_mockbean_external_clients).
 *
 * <p>UUID 비공개 가드 — 본 client 결과는 service 레이어 내부 검증용으로만 사용, 사용자 화면 직접
 * 노출 X.
 */
@Slf4j
@Component
public class UserClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient.Builder builder;
    private final ServiceDiscoveryClient discoveryClient;
    private final String baseUrl;
    private final String internalToken;
    private final UserCacheProperties cacheProperties;

    private Cache<UUID, Boolean> existsCache;

    public UserClient(RestClient.Builder builder,
                      ServiceDiscoveryClient discoveryClient,
                      @Value("${samhan.user-service.url:http://localhost:8083}") String baseUrl,
                      @Value("${app.security.internal.token:}") String internalToken,
                      UserCacheProperties cacheProperties) {
        this.builder = builder;
        this.discoveryClient = discoveryClient;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.cacheProperties = cacheProperties;
    }

    @PostConstruct
    void initCache() {
        this.existsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheProperties.getTtlSeconds()))
                .maximumSize(cacheProperties.getMaxSize())
                .build();
    }

    /**
     * 사용자 존재 검증 — 단건. 200 OK = 존재, 404 = 미존재. Caffeine cache hit 시 RPC skip.
     *
     * @param userId user UUID
     * @return 존재 시 {@code true}, 404 시 {@code false}
     */
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

    /**
     * 사용자 존재 검증 — bulk. Phase 9 W3 BE backlog #4 채택. cache hit 한 ID 는 RPC 호출 skip,
     * 미스 ID 만 1회 bulk RPC 로 검증.
     *
     * @param userIds user UUID list (null / empty 허용 — 빈 결과)
     * @return userId → exists 매핑 (모든 입력에 대해 응답 포함)
     */
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
            RestClient client = builder.baseUrl(baseUrl).build();
            String response = client.post()
                    .uri("/internal/users/verify-bulk")
                    .header("X-Internal-Token", internalToken)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
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
            // 응답에 누락된 ID 는 미존재로 간주 + cache miss 회피 위해 false 캐시는 하지 않음 (재호출 가능).
            for (UUID id : missing) {
                result.putIfAbsent(id, false);
            }
            return result;
        } catch (Exception ex) {
            log.warn("UserClient bulk verify 실패 — size={} msg={} (skeleton 정책: 모두 통과)",
                    missing.size(), ex.getMessage());
            // 네트워크 / discovery 실패 — W3 skeleton 정책 (PR #92 단건 정책과 일관: 통과).
            // Phase 10 cutover 시점에 fail-fast 정책으로 강화.
            for (UUID id : missing) {
                result.putIfAbsent(id, true);
            }
            return result;
        }
    }

    private boolean callExists(UUID userId) {
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            client.get()
                    .uri("/internal/users/{id}", userId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            log.warn("UserClient lookup 예외 — userId={}, status={}", userId, ex.getStatusCode());
            throw ex;
        } catch (Exception ex) {
            // 네트워크 / discovery 실패 — 본 service 의 검증을 차단하기보다 통과 (W3 skeleton 정책).
            // Phase 10 cutover 시점에 fail-fast 정책으로 강화.
            log.warn("UserClient lookup 실패 — userId={}, msg={}", userId, ex.getMessage());
            return true;
        }
    }

    /** Phase 10 활성 대비 — discovery client 보유 검증 (현재 미사용). */
    public ServiceDiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }

    /** 단위 테스트용 — cache 명시 invalidate. */
    public void invalidateCache() {
        if (existsCache != null) {
            existsCache.invalidateAll();
        }
    }
}
