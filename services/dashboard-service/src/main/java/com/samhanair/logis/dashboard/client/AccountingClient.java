package com.samhanair.logis.dashboard.client;

import com.samhanair.logis.discovery.ServiceDiscoveryClient;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * accounting-service (8087) 호출 client — 매출 데이터 집계용.
 *
 * <p>Phase 9 W4 — skeleton fail-soft 정책. accounting-service 가 dashboard 전용 매출 집계
 * endpoint 를 노출하기 전까지는 BigDecimal.ZERO 반환 (호출 자체는 발생).
 *
 * <p>Phase 10 cutover 시점에 accounting-service 의 trial-balance + journal 합계 API 와 통합.
 *
 * <p>IT 에서는 {@code @MockBean AccountingClient} 격리 의무.
 */
@Slf4j
@Component
public class AccountingClient {

    private final RestClient.Builder builder;
    private final ServiceDiscoveryClient discoveryClient;
    private final String baseUrl;
    private final String internalToken;

    public AccountingClient(RestClient.Builder builder,
                             ServiceDiscoveryClient discoveryClient,
                             @Value("${samhan.accounting-service.url:http://localhost:8087}") String baseUrl,
                             @Value("${app.security.internal.token:}") String internalToken) {
        this.builder = builder;
        this.discoveryClient = discoveryClient;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    /**
     * 거래처 + 일자 범위에 해당하는 매출 합계 lookup.
     *
     * @param partnerId 거래처 UUID
     * @param from 시작 일자 (inclusive)
     * @param to 종료 일자 (inclusive)
     * @return 합계 금액 (skeleton 단계 — 외부 호출 후 실패 시 ZERO)
     */
    public BigDecimal sumSalesByPartner(UUID partnerId, LocalDate from, LocalDate to) {
        if (partnerId == null || from == null || to == null) {
            return BigDecimal.ZERO;
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            String body = client.get()
                    .uri("/internal/sales?partnerId={pid}&from={from}&to={to}",
                            partnerId, from.toString(), to.toString())
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(String.class);
            log.debug("AccountingClient sales lookup body length={} (parsing deferred to Phase 10)",
                    body == null ? 0 : body.length());
            return BigDecimal.ZERO;
        } catch (Exception ex) {
            log.warn("AccountingClient sales lookup 실패 — partnerId={}, msg={}", partnerId, ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /** Phase 10 활성 대비 — discovery client 보유 검증 (현재 미사용). */
    public ServiceDiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }
}
