package com.samhanair.logis.arologis.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * partner-service (8095, W1) 호출 client — Phase 10 W10-1 arologis-service.
 *
 * <p>카톡 파싱 시 파싱된 partnerCode (전표번호) 매핑 검증. 본 PR (W10-1) 은 skeleton-mode 기본값
 * (외부 호출 회피, empty 반환). W10-2 시점 매칭 trigger 단계에서 실 호출 활성.
 *
 * <p>partner-service `POST /internal/partners/find-by-codes` (W5 endpoint, PR #95) 활용.
 */
@Slf4j
@Component
public class PartnerClient {

    private final RestClient.Builder builder;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String internalToken;
    private final boolean skeletonMode;

    public PartnerClient(RestClient.Builder builder,
                         ObjectMapper objectMapper,
                         @Value("${samhan.partner-service.url:http://localhost:8095}") String baseUrl,
                         @Value("${app.security.internal.token:}") String internalToken,
                         @Value("${samhan.arologis.client.skeleton-mode:true}") boolean skeletonMode) {
        this.builder = builder;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.skeletonMode = skeletonMode;
    }

    /**
     * partnerCode N건 bulk lookup — 카톡 파싱된 전표번호 매핑 검증.
     *
     * <p>skeleton-mode 또는 입력 비어있을 때 빈 리스트. 실 호출은 W10-2 시점 매칭 단계.
     */
    public List<PartnerSummary> findByCodes(List<String> partnerCodes) {
        if (partnerCodes == null || partnerCodes.isEmpty()) {
            return Collections.emptyList();
        }
        if (skeletonMode) {
            log.debug("PartnerClient.findByCodes skeleton-mode — codes={} (외부 호출 회피, W10-1 default)",
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
            log.warn("PartnerClient.findByCodes 예외 — codes={}, status={}",
                    partnerCodes.size(), ex.getStatusCode());
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("PartnerClient.findByCodes 실패 — codes={}, msg={}",
                    partnerCodes.size(), ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * partnerCode 단건 lookup — 자동 매칭 단계에서 단일 정차 partner 검증용.
     */
    public Optional<PartnerSummary> findByCode(String partnerCode) {
        if (partnerCode == null || partnerCode.isBlank() || skeletonMode) {
            return Optional.empty();
        }
        List<PartnerSummary> result = findByCodes(List.of(partnerCode));
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

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
                JsonNode codeNode = node.get("partnerCode");
                JsonNode nameNode = node.get("name");
                if (codeNode == null || codeNode.isNull()) {
                    continue;
                }
                out.add(new PartnerSummary(
                        codeNode.asText(),
                        nameNode == null || nameNode.isNull() ? null : nameNode.asText()));
            }
            return out;
        } catch (Exception ex) {
            log.warn("PartnerClient response 파싱 실패 — bodyLen={}, msg={}",
                    body.length(), ex.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * partner-service 응답 요약 (UUID 비공개 가드 — partnerId UUID 제외, code/name 만 노출).
     */
    public record PartnerSummary(String partnerCode, String name) {}
}
