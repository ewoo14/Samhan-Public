package com.samhanair.logis.dashboard.client;

import com.samhanair.logis.discovery.ServiceDiscoveryClient;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * inventory-service (8085) 호출 client — 실시간 재고 lookup.
 *
 * <p>Phase 9 W4 — ServiceDiscoveryClient 네 번째 소비자 진입점 중 하나. inventory-service 의
 * stock endpoint 가 W4 시점에는 dashboard 전용 internal endpoint 를 보유하지 않으므로 본 client
 * 는 fail-soft 정책 (네트워크 실패 / 404 시 empty Optional) 을 default 로 보유.
 *
 * <p>Phase 10 cutover 시점에 inventory-service 가 dashboard 전용
 * {@code GET /internal/stock?productId=&warehouseCode=} endpoint 를 노출하면 본 client
 * 가 실 RPC 로 전환 (현 시점 stub).
 *
 * <p>IT 에서는 {@code @MockBean InventoryClient} 격리 의무 (memory feedback_it_mockbean_external_clients).
 */
@Slf4j
@Component
public class InventoryClient {

    private final RestClient.Builder builder;
    private final ServiceDiscoveryClient discoveryClient;
    private final String baseUrl;
    private final String internalToken;

    public InventoryClient(RestClient.Builder builder,
                           ServiceDiscoveryClient discoveryClient,
                           @Value("${samhan.inventory-service.url:http://localhost:8085}") String baseUrl,
                           @Value("${app.security.internal.token:}") String internalToken) {
        this.builder = builder;
        this.discoveryClient = discoveryClient;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    /**
     * 실시간 재고 lookup — productId + warehouseCode 조합. 본 W4 skeleton 시점에는 fail-soft
     * 정책 (네트워크 실패 / 404 시 empty Optional).
     *
     * @param productId 제품 UUID
     * @param warehouseCode 창고 코드
     * @return 재고 수량 (있으면 Optional.of, 없으면 empty)
     */
    public Optional<BigDecimal> findStock(UUID productId, String warehouseCode) {
        if (productId == null || warehouseCode == null || warehouseCode.isBlank()) {
            return Optional.empty();
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            String body = client.get()
                    .uri("/internal/stock?productId={pid}&warehouseCode={wc}", productId, warehouseCode)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(String.class);
            // skeleton 단계 — 응답 파싱은 Phase 10 시점에 inventory-service Internal API 정착 시점에 구현.
            log.debug("InventoryClient stock lookup body length={} (parsing deferred to Phase 10)",
                    body == null ? 0 : body.length());
            return Optional.empty();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("InventoryClient lookup 예외 — productId={}, status={}", productId, ex.getStatusCode());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("InventoryClient lookup 실패 — productId={}, warehouse={}, msg={}",
                    productId, warehouseCode, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Phase 10 활성 대비 — discovery client 보유 검증 (현재 미사용). */
    public ServiceDiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }
}
