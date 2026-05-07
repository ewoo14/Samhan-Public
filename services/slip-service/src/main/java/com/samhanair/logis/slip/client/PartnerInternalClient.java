package com.samhanair.logis.slip.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.slip.config.InternalAuthProperties;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * partner-service 호출 client — Phase 10 W10-4 종합 TM (BE-1 채택) 신규.
 *
 * <p>arologis-service 가 slip-service 의 by-partner-code endpoint 를 호출하면 slip-service 는 본
 * client 로 partner-service {@code GET /internal/partners/{partnerCode}} 를 호출하여 partnerId UUID
 * 를 resolve 후 자체 SlipRepository 로 활성 슬립을 lookup 한다.
 *
 * <p>endpoint 1종:
 * <ul>
 *   <li>{@code GET /internal/partners/{partnerCode}} → PartnerInternalResponse (partnerId UUID 포함).
 *       PartnerInternalResponse 는 partner-service 만 알고 있으므로 본 client 는 raw JsonNode 에서
 *       partnerId 만 추출 (의존성 최소화).</li>
 * </ul>
 *
 * <p>인증 = X-Internal-Token (partner-service 의 InternalTokenFilter 가 ROLE_MASTER 부여).
 *
 * <p>오류 처리 (graceful fallback):
 * <ul>
 *   <li>4xx (404 = 미존재) → empty Optional. 호출자(slip-service)가 NOT_FOUND 매핑.</li>
 *   <li>5xx / 연결 실패 → empty Optional + warn log. arologis 자체 INSERT 만 유지 운영 영향 0.</li>
 *   <li>internal token 미설정 → empty Optional + warn log.</li>
 * </ul>
 *
 * <p>timeout 설정 (DV-1 채택 일관) — connect 2s / read 3s. partner-service hang 시 slip-service
 * by-partner-code endpoint 가 driver-app 응답을 차단하지 않도록.
 */
@Component
public class PartnerInternalClient {

    private static final Logger log = LoggerFactory.getLogger(PartnerInternalClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String PARTNER_SERVICE_BASE = "http://partner-service";

    private final RestClient restClient;
    private final InternalAuthProperties internalAuthProperties;
    private final ObjectMapper objectMapper;

    public PartnerInternalClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
                                 InternalAuthProperties internalAuthProperties,
                                 ObjectMapper objectMapper) {
        // DV-1 채택 일관 — connect 2s / read 3s (partner-service hang SLA 가드).
        // Spring Boot 3.3 + JDK SimpleClientHttpRequestFactory 표준 setter 사용
        // (Spring Boot 3.4 의 ClientHttpRequestFactories 표준 키는 미지원 단계).
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        rf.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        this.restClient = builder
                .baseUrl(PARTNER_SERVICE_BASE)
                .requestFactory(rf)
                .build();
        this.internalAuthProperties = internalAuthProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * partnerCode → partnerId UUID resolve.
     *
     * @param partnerCode 사용자 노출 식별자 (카톡 파싱 결과 또는 dispatch stop.parsedPartnerCode)
     * @return partnerId UUID Optional. 미존재 / 5xx / 연결 실패 / 토큰 미설정 시 empty.
     */
    public Optional<UUID> resolvePartnerId(String partnerCode) {
        if (partnerCode == null || partnerCode.isBlank()) {
            return Optional.empty();
        }
        String token = internalAuthProperties.getToken();
        if (token == null || token.isBlank()) {
            log.warn("PartnerInternalClient.resolvePartnerId — app.security.internal.token 미설정, empty 반환 (partnerCode={})",
                    partnerCode);
            return Optional.empty();
        }
        try {
            String body = restClient.get()
                    .uri("/internal/partners/{partnerCode}", partnerCode)
                    .header(INTERNAL_TOKEN_HEADER, token)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.has("data") ? root.get("data") : root;
            if (data == null || data.isNull()) {
                return Optional.empty();
            }
            JsonNode partnerIdNode = data.get("partnerId");
            if (partnerIdNode == null || partnerIdNode.isNull()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(partnerIdNode.asText()));
        } catch (RestClientResponseException ex) {
            // 404 = 미등록 partnerCode (정상). 5xx = warn.
            if (ex.getStatusCode().is5xxServerError()) {
                log.warn("PartnerInternalClient.resolvePartnerId 5xx — partnerCode={}, status={}",
                        partnerCode, ex.getStatusCode());
            } else {
                log.debug("PartnerInternalClient.resolvePartnerId 4xx (미존재 등) — partnerCode={}, status={}",
                        partnerCode, ex.getStatusCode());
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("PartnerInternalClient.resolvePartnerId 호출 실패 — partnerCode={}, msg={}",
                    partnerCode, ex.getMessage());
            return Optional.empty();
        }
    }
}
