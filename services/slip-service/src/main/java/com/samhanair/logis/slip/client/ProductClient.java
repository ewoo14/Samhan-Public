package com.samhanair.logis.slip.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.config.InternalAuthProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Internal-token-authenticated client to {@code product-service}'s
 * {@code /products/internal/lookup} batch endpoint. slip-service 가 라인의 productId 를
 * 받을 때마다 이 클라이언트로 존재 여부를 검증한다.
 *
 * <p>4xx → BusinessException(INVALID_INPUT, "존재하지 않는 제품 ID")<br>
 * 5xx / connection refused → BusinessException(INTERNAL_ERROR, "product-service 호출 실패")<br>
 * 1건이라도 응답에 없으면 BusinessException(NOT_FOUND).
 */
@Component
public class ProductClient {

    private static final Logger log = LoggerFactory.getLogger(ProductClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String PRODUCT_SERVICE_BASE = "http://product-service";
    private static final int LOOKUP_BATCH_MAX = 100;

    private final RestClient restClient;
    private final InternalAuthProperties internalAuthProperties;
    private final ObjectMapper objectMapper;

    public ProductClient(@Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
                         InternalAuthProperties internalAuthProperties,
                         ObjectMapper objectMapper) {
        this.restClient = builder.baseUrl(PRODUCT_SERVICE_BASE).build();
        this.internalAuthProperties = internalAuthProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * product-service 의 {@code POST /products/internal/lookup} 을 호출해 productId 리스트의
     * 존재 여부를 일괄 검증한다. X-Internal-Token 헤더로 인증.
     *
     * @param productIds 조회할 제품 UUID 리스트 (1 ~ {@value #LOOKUP_BATCH_MAX} 건)
     * @return 입력 순서와 무관한 ProductSummary 리스트
     * @throws BusinessException(INVALID_INPUT) productIds null/empty 또는 batch 한도 초과,
     *         혹은 product-service 가 4xx 반환 (존재하지 않는 ID 포함)
     * @throws BusinessException(NOT_FOUND) 응답 항목 수 &lt; 요청 수
     * @throws BusinessException(INTERNAL_ERROR) product-service 5xx, 연결 실패, envelope 포맷 오류,
     *         혹은 internal token 미설정
     */
    @SuppressWarnings("unchecked")
    public List<ProductSummary> lookup(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회할 제품 ID가 비어있습니다");
        }
        if (productIds.size() > LOOKUP_BATCH_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "한 번에 조회할 수 있는 최대 제품 수는 " + LOOKUP_BATCH_MAX + "건입니다");
        }

        Map<String, Object> body = Map.of(
                "ids", productIds.stream().map(UUID::toString).toList());

        Map<String, Object> envelope;
        try {
            envelope = restClient.post()
                    .uri("/products/internal/lookup")
                    .header(INTERNAL_TOKEN_HEADER, requireToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new BusinessException(ErrorCode.INVALID_INPUT,
                                "존재하지 않는 제품 ID");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                                "product-service 호출 실패: " + res.getStatusCode());
                    })
                    .body(new ParameterizedTypeReference<>() {});
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("ProductClient lookup failed: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "product-service 호출 실패", ex);
        }

        Object data = envelope == null ? null : envelope.get("data");
        if (!(data instanceof List<?> rawList)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "product-service 응답 포맷 오류 (data 누락)");
        }
        List<ProductSummary> summaries = ((List<Object>) rawList).stream()
                .map(item -> objectMapper.convertValue(item, ProductSummary.class))
                .toList();

        if (summaries.size() < productIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "일부 제품을 찾을 수 없습니다 (요청 " + productIds.size()
                            + ", 응답 " + summaries.size() + ")");
        }
        return summaries;
    }

    /**
     * 단건 검증 편의 — {@link #lookup(List)} 1건 호출 후 첫 항목 반환.
     *
     * @param productId 조회할 제품 UUID
     * @return product-service 의 ProductSummary
     */
    public ProductSummary requireExists(UUID productId) {
        return lookup(List.of(productId)).get(0);
    }

    private String requireToken() {
        String token = internalAuthProperties.getToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "app.security.internal.token 미설정");
        }
        return token;
    }
}
