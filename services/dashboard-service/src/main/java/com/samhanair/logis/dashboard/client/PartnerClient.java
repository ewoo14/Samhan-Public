package com.samhanair.logis.dashboard.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.discovery.ServiceDiscoveryClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
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

    /**
     * partnerCode N건 bulk lookup — Phase 9 W5 신규 (D-P9-16, BE 의견 3 채택).
     *
     * <p>partner-service `POST /internal/partners/find-by-codes` 호출 (직렬 N회 RPC → 1회 batch).
     * 입력 비어있거나 skeleton-mode 시 빈 리스트 반환 (외부 호출 회피). 응답 파싱은 단건 lookup 의
     * {@link #parseSummary(String)} 패턴 일관 — ApiResponse wrapper 의 {@code data} 배열에서 각 row
     * 를 PartnerSummary 로 변환. 네트워크 / 4xx / 5xx 실패 시 빈 리스트 (fail-soft, 단건 lookup 의
     * Optional.empty 일관).
     *
     * @param partnerCodes 조회할 partnerCode 모음 (null/empty → 빈 리스트)
     * @return 매칭된 PartnerSummary 리스트 (skeleton-mode / 실패 시 빈 리스트)
     */
    public List<PartnerSummary> findByCodes(List<String> partnerCodes) {
        if (partnerCodes == null || partnerCodes.isEmpty()) {
            return Collections.emptyList();
        }
        if (skeletonMode) {
            log.debug("PartnerClient.findByCodes skeleton-mode — codes={} (외부 호출 회피)",
                    partnerCodes.size());
            return Collections.emptyList();
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            String body = client.post()
                    .uri("/internal/partners/find-by-codes")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(partnerCodes)
                    .retrieve()
                    .body(String.class);
            return parseSummaryList(body);
        } catch (RestClientResponseException ex) {
            log.warn("PartnerClient.findByCodes lookup 예외 — codes={}, status={}",
                    partnerCodes.size(), ex.getStatusCode());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("PartnerClient.findByCodes lookup 실패 — codes={}, msg={}",
                    partnerCodes.size(), ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * partner-service ApiResponse wrapper 내부 {@code data} 배열에서 PartnerSummary 리스트 추출.
     * 형식이 다르거나 파싱 실패 시 빈 리스트.
     */
    private List<PartnerSummary> parseSummaryList(String body) {
        if (body == null || body.isBlank()) {
            return Collections.emptyList();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data == null || !data.isArray()) {
                return Collections.emptyList();
            }
            List<PartnerSummary> out = new ArrayList<>(data.size());
            for (JsonNode node : data) {
                JsonNode idNode = node.get("partnerId");
                JsonNode codeNode = node.get("partnerCode");
                JsonNode nameNode = node.get("name");
                if (idNode == null || idNode.isNull() || codeNode == null || codeNode.isNull()) {
                    continue;
                }
                out.add(new PartnerSummary(
                        UUID.fromString(idNode.asText()),
                        codeNode.asText(),
                        nameNode == null || nameNode.isNull() ? null : nameNode.asText()));
            }
            return out;
        } catch (Exception ex) {
            log.warn("PartnerClient.findByCodes response 파싱 실패 — bodyLen={}, msg={}",
                    body.length(), ex.getMessage());
            return Collections.emptyList();
        }
    }

    /** Phase 10 활성 대비 — discovery client 보유 검증 (현재 미사용). */
    public ServiceDiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }
}
