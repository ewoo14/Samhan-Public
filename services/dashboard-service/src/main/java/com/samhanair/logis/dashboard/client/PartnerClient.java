package com.samhanair.logis.dashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.discovery.ServiceDiscoveryClient;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * partner-service (8095, W1) 호출 client — 거래처 정보 lookup.
 *
 * <p>partnerCode → partnerName / partnerType / partnerId 매핑. UUID 비공개 가드 일관 — 사용자 화면에서
 * partnerId 노출 금지, 본 client 는 partnerCode → name + UUID 변환 책임 (UUID 는 dashboard 내부 only).
 *
 * <p>Phase 9 W4 — partner-service `/internal/partners/{partnerCode}` 활용 (W1 신규 endpoint).
 *
 * <p>IT 에서는 {@code @MockBean PartnerClient} 격리 의무.
 *
 * <p>PR #94 W4 후속 fix (QA Q-W4-2 채택) — 응답 raw String → {@link PartnerSummary} record 강화.
 * service-side resolve (partnerCode → partnerId UUID) 를 가능하게 하여 admin endpoint 의 UUID
 * 입력 파라미터 제거 (UUID 비공개 가드 일관).
 *
 * <p>PR #94 W4 후속 fix (BE 의견 2 채택) — {@code samhan.dashboard.client.skeleton-mode} 토글.
 * skeleton-mode true (W4 default) 시 외부 호출 회피 + Optional.empty 반환.
 */
@Slf4j
@Component
public class PartnerClient {

    private final RestClient.Builder builder;
    private final ServiceDiscoveryClient discoveryClient;
    private final String baseUrl;
    private final String internalToken;
    private final boolean skeletonMode;
    private final ObjectMapper objectMapper;

    public PartnerClient(RestClient.Builder builder,
                          ServiceDiscoveryClient discoveryClient,
                          ObjectMapper objectMapper,
                          @Value("${samhan.partner-service.url:http://localhost:8095}") String baseUrl,
                          @Value("${app.security.internal.token:}") String internalToken,
                          @Value("${samhan.dashboard.client.skeleton-mode:true}") boolean skeletonMode) {
        this.builder = builder;
        this.discoveryClient = discoveryClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.skeletonMode = skeletonMode;
    }

    /**
     * partnerCode 로 거래처 lookup. 200 + 응답 body 보유 시 {@link PartnerSummary} 반환.
     * 404 / 네트워크 실패 / skeleton-mode 시 empty.
     *
     * <p>응답 형식 — partner-service {@code PartnerInternalResponse} 의
     * {@code data} 부분 (ApiResponse wrapper 내부) 에서 partnerId / partnerCode / name 추출.
     *
     * @param partnerCode 거래처 코드 (사용자 노출 식별자)
     * @return PartnerSummary (있으면 Optional.of, 없으면 empty)
     */
    public Optional<PartnerSummary> findByCode(String partnerCode) {
        if (partnerCode == null || partnerCode.isBlank()) {
            return Optional.empty();
        }
        if (skeletonMode) {
            log.debug("PartnerClient skeleton-mode — partnerCode={} (외부 호출 회피, empty 반환)", partnerCode);
            return Optional.empty();
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            String body = client.get()
                    .uri("/internal/partners/{code}", partnerCode)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(String.class);
            return parseSummary(body);
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

    /**
     * partner-service ApiResponse wrapper 내부에서 PartnerSummary 추출. 형식이 다르면 empty.
     *
     * <p>예상 응답: {@code {"success":true,"data":{"partnerId":"...","partnerCode":"...","name":"..."}, ...}}
     */
    private Optional<PartnerSummary> parseSummary(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data == null || data.isNull() || !data.isObject()) {
                return Optional.empty();
            }
            JsonNode idNode = data.get("partnerId");
            JsonNode codeNode = data.get("partnerCode");
            JsonNode nameNode = data.get("name");
            if (idNode == null || idNode.isNull() || codeNode == null || codeNode.isNull()) {
                return Optional.empty();
            }
            return Optional.of(new PartnerSummary(
                    UUID.fromString(idNode.asText()),
                    codeNode.asText(),
                    nameNode == null || nameNode.isNull() ? null : nameNode.asText()));
        } catch (Exception ex) {
            log.warn("PartnerClient response 파싱 실패 — bodyLen={}, msg={}",
                    body.length(), ex.getMessage());
            return Optional.empty();
        }
    }

    /** Phase 10 활성 대비 — discovery client 보유 검증 (현재 미사용). */
    public ServiceDiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }
}
