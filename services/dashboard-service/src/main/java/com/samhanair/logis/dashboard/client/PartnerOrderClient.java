package com.samhanair.logis.dashboard.client;

import com.samhanair.logis.discovery.ServiceDiscoveryClient;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * partner-order-service (8088) 호출 client — 주문 건수 집계용.
 *
 * <p>Phase 9 W4 — skeleton fail-soft 정책. partner-order-service 의 confirm 흐름 outbox 데이터를
 * dashboard 가 집계 lookup. 실 endpoint 통합은 Phase 10 cutover 시점.
 *
 * <p>IT 에서는 {@code @MockBean PartnerOrderClient} 격리 의무.
 */
@Slf4j
@Component
public class PartnerOrderClient {

    private final RestClient.Builder builder;
    private final ServiceDiscoveryClient discoveryClient;
    private final String baseUrl;
    private final String internalToken;

    public PartnerOrderClient(RestClient.Builder builder,
                               ServiceDiscoveryClient discoveryClient,
                               @Value("${samhan.partner-order-service.url:http://localhost:8088}") String baseUrl,
                               @Value("${app.security.internal.token:}") String internalToken) {
        this.builder = builder;
        this.discoveryClient = discoveryClient;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    /**
     * 거래처 + 일자 범위에 해당하는 주문 건수 lookup.
     *
     * @param partnerId 거래처 UUID
     * @param from 시작 일자
     * @param to 종료 일자
     * @return 주문 건수 (skeleton 단계 — 호출 실패 시 0)
     */
    public int countOrdersByPartner(UUID partnerId, LocalDate from, LocalDate to) {
        if (partnerId == null || from == null || to == null) {
            return 0;
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            String body = client.get()
                    .uri("/internal/orders/count?partnerId={pid}&from={from}&to={to}",
                            partnerId, from.toString(), to.toString())
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(String.class);
            log.debug("PartnerOrderClient count lookup body length={} (parsing deferred to Phase 10)",
                    body == null ? 0 : body.length());
            return 0;
        } catch (Exception ex) {
            log.warn("PartnerOrderClient count 실패 — partnerId={}, msg={}", partnerId, ex.getMessage());
            return 0;
        }
    }

    /** Phase 10 활성 대비 — discovery client 보유 검증 (현재 미사용). */
    public ServiceDiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }
}
