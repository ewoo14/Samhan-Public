package com.samhanair.logis.dashboard.client;

import com.samhanair.logis.discovery.ServiceDiscoveryClient;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * partner-service (8095, W1) 호출 client — 거래처 정보 lookup.
 *
 * <p>partnerCode → partnerName / partnerType 매핑. UUID 비공개 가드 일관 — 사용자 화면에서
 * partnerId 노출 금지, 본 client 는 partnerCode → name 변환 책임.
 *
 * <p>Phase 9 W4 — partner-service `/internal/partners/{partnerCode}` 활용 (W1 신규 endpoint).
 *
 * <p>IT 에서는 {@code @MockBean PartnerClient} 격리 의무.
 */
@Slf4j
@Component
public class PartnerClient {

    private final RestClient.Builder builder;
    private final ServiceDiscoveryClient discoveryClient;
    private final String baseUrl;
    private final String internalToken;

    public PartnerClient(RestClient.Builder builder,
                          ServiceDiscoveryClient discoveryClient,
                          @Value("${samhan.partner-service.url:http://localhost:8095}") String baseUrl,
                          @Value("${app.security.internal.token:}") String internalToken) {
        this.builder = builder;
        this.discoveryClient = discoveryClient;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    /**
     * partnerCode 로 거래처 lookup. 200 + 응답 body 보유 시 raw JSON 반환 (skeleton 단계 파싱 미수행),
     * 404 / 네트워크 실패 시 empty.
     *
     * @param partnerCode 거래처 코드 (사용자 노출 식별자)
     * @return 거래처 정보 raw JSON (Phase 10 시점 DTO 파싱 도입 예정)
     */
    public Optional<String> findByCode(String partnerCode) {
        if (partnerCode == null || partnerCode.isBlank()) {
            return Optional.empty();
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            String body = client.get()
                    .uri("/internal/partners/{code}", partnerCode)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(String.class);
            return Optional.ofNullable(body);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("PartnerClient lookup 예외 — partnerCode={}, status={}", partnerCode, ex.getStatusCode());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("PartnerClient lookup 실패 — partnerCode={}, msg={}", partnerCode, ex.getMessage());
            return Optional.empty();
        }
    }

    /** Phase 10 활성 대비 — discovery client 보유 검증 (현재 미사용). */
    public ServiceDiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }
}
