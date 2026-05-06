package com.samhanair.logis.groupware.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.discovery.ServiceDiscoveryClient;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * user-service 호출 client — 직원 정보 lookup ({@code GET /internal/users/{userId}}) + bulk
 * verify ({@code POST /internal/users/verify-bulk}).
 *
 * <p>Phase 9 W3 — BE backlog #4 채택. ApprovalLineService.create 의 N 결재자 fan-out 직렬 RPC
 * 비용 해소 — bulk verify 1회 호출로 통합 (notification-service 의 동일 client 패턴 일관).
 *
 * <p>ServiceDiscoveryClient 두 번째 소비자 (W1 partner-service 첫 소비자). {@code samhan.discovery.provider}
 * = eureka default — 별도 추가 의존성 없음 (build.gradle 의 {@code shared:discovery-abstraction} 의존).
 *
 * <p>IT 에서는 {@code @MockBean UserClient} 격리 의무 (memory feedback_it_mockbean_external_clients).
 *
 * <p>UUID 비공개 가드 — 본 client 결과는 service 레이어 내부 검증용으로만 사용, 사용자 화면
 * 직접 노출 X.
 */
@Slf4j
@Component
public class UserClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient.Builder builder;
    private final ServiceDiscoveryClient discoveryClient;
    private final String baseUrl;
    private final String internalToken;

    public UserClient(RestClient.Builder builder,
                      ServiceDiscoveryClient discoveryClient,
                      @Value("${samhan.user-service.url:http://localhost:8083}") String baseUrl,
                      @Value("${app.security.internal.token:}") String internalToken) {
        this.builder = builder;
        this.discoveryClient = discoveryClient;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    /**
     * 사용자 존재 검증. 200 OK = 존재, 404 = 미존재. 그 외 응답은 예외 전파.
     *
     * @param userId user UUID
     * @return 존재 시 {@code true}, 404 시 {@code false}
     */
    public boolean exists(UUID userId) {
        if (userId == null) {
            return false;
        }
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
            // 네트워크 / discovery 실패 — 본 service 의 검증을 차단하기보다 통과 (skeleton 정책).
            // Phase 10 cutover 시점에 fail-fast 정책으로 강화.
            log.warn("UserClient lookup 실패 — userId={}, msg={}", userId, ex.getMessage());
            return true;
        }
    }

    /**
     * 사용자 다건 존재 검증 — Phase 9 W3 BE backlog #4 채택. fan-out 직렬 lookup 회피.
     *
     * @param userIds user UUID 목록
     * @return userId → exists 매핑 (모든 입력 포함). 누락된 입력은 false.
     */
    public Map<UUID, Boolean> verifyBulk(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> distinct = new HashSet<>();
        for (UUID id : userIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            String response = client.post()
                    .uri("/internal/users/verify-bulk")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userIds", new ArrayList<>(distinct)))
                    .retrieve()
                    .body(String.class);

            JsonNode root = MAPPER.readTree(response == null ? "{}" : response);
            JsonNode data = root.has("data") ? root.path("data") : root;
            JsonNode existsNode = data.path("exists");
            Map<UUID, Boolean> result = new HashMap<>();
            if (existsNode.isObject()) {
                existsNode.fieldNames().forEachRemaining(idStr -> {
                    try {
                        UUID id = UUID.fromString(idStr);
                        boolean ok = existsNode.path(idStr).asBoolean(false);
                        result.put(id, ok);
                    } catch (IllegalArgumentException ignored) {
                        // skip
                    }
                });
            }
            for (UUID id : distinct) {
                result.putIfAbsent(id, false);
            }
            return result;
        } catch (Exception ex) {
            log.warn("UserClient bulk verify 실패 — size={} msg={} (skeleton 정책: 모두 통과)",
                    distinct.size(), ex.getMessage());
            Map<UUID, Boolean> result = new HashMap<>();
            for (UUID id : distinct) {
                result.put(id, true);
            }
            return result;
        }
    }

    /** Phase 10 활성 대비 — discovery client 보유 검증 (현재 미사용). */
    public ServiceDiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }
}
