package com.samhanair.logis.groupware.client;

import com.samhanair.logis.discovery.ServiceDiscoveryClient;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * user-service 호출 client — 직원 정보 lookup ({@code GET /internal/users/{userId}}).
 *
 * <p>본 client 는 결재선 / 메신저 / 일정 처리 시 user 식별자가 실제 존재하는지 검증하는 보조
 * dependency 다. 본 PR (W2 skeleton) 시점 = client class + verification API stub 만 작성, 실제
 * user-service Internal endpoint 호출 검증은 Phase 9 W5 또는 Phase 10 cutover 시점에 통합 검증.
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
            // 네트워크 / discovery 실패 — 본 service 의 검증을 차단하기보다 통과 (W2 skeleton 정책).
            // Phase 10 cutover 시점에 fail-fast 정책으로 강화.
            log.warn("UserClient lookup 실패 — userId={}, msg={}", userId, ex.getMessage());
            return true;
        }
    }

    /** Phase 10 활성 대비 — discovery client 보유 검증 (현재 미사용). */
    public ServiceDiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }
}
