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
 *
 * <p>PR #94 W4 후속 fix (BE 의견 2 채택) — {@code samhan.dashboard.client.skeleton-mode} 토글.
 * skeleton-mode true (W4 default) 시 외부 호출 회피 + default 반환 (skeleton 의도 명확화).
 * false 시 Phase 10 cutover — 실 호출 + 응답 파싱은 cutover 시점 BE 슬라이스에서 구현.
 */
@Slf4j
@Component
public class InventoryClient {

    private final RestClient.Builder builder;
    private final ServiceDiscoveryClient discoveryClient;
    private final String baseUrl;
    private final String internalToken;
    private final boolean skeletonMode;

    public InventoryClient(RestClient.Builder builder,
                           ServiceDiscoveryClient discoveryClient,
                           @Value("${samhan.inventory-service.url:http://localhost:8085}") String baseUrl,
                           @Value("${app.security.internal.token:}") String internalToken,
                           @Value("${samhan.dashboard.client.skeleton-mode:true}") boolean skeletonMode) {
        this.builder = builder;
        this.discoveryClient = discoveryClient;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.skeletonMode = skeletonMode;
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
        if (skeletonMode) {
            log.debug("InventoryClient skeleton-mode — productId={}, warehouseCode={} (외부 호출 회피, empty 반환)",
                    productId, warehouseCode);
            return Optional.empty();
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            String body = client.get()
                    .uri("/internal/stock?productId={pid}&warehouseCode={wc}", productId, warehouseCode)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(String.class);
            // Phase 10 cutover 시점 응답 파싱 활성 (현재는 미구현).
            log.debug("InventoryClient stock lookup body length={}", body == null ? 0 : body.length());
            throw new UnsupportedOperationException(
                    "InventoryClient body 파싱은 Phase 10 cutover 시점에 활성됩니다 (skeleton-mode=false 진입 전 BE 슬라이스 구현 의무).");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("InventoryClient lookup 예외 — productId={}, status={}", productId, ex.getStatusCode());
            return Optional.empty();
        } catch (UnsupportedOperationException ex) {
            throw ex;
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
