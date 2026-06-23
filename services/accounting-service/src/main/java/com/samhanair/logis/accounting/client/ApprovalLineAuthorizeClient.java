package com.samhanair.logis.accounting.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.security.InternalAuthProperties;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** auth-service 결재라인 내부 인가 client. */
@Component
public class ApprovalLineAuthorizeClient {

    private static final Logger log = LoggerFactory.getLogger(ApprovalLineAuthorizeClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String AUTH_SERVICE_BASE = "http://auth-service";

    private final RestClient restClient;
    private final InternalAuthProperties internalAuthProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public ApprovalLineAuthorizeClient(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder,
            InternalAuthProperties internalAuthProperties,
            ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        rf.setReadTimeout((int) Duration.ofSeconds(3).toMillis());
        this.restClient = builder
                .baseUrl(AUTH_SERVICE_BASE)
                .requestFactory(rf)
                .build();
        this.internalAuthProperties = internalAuthProperties;
        this.objectMapper = objectMapper;
    }

    /** 테스트 전용 생성자 — MockRestServiceServer 에 바인딩된 RestClient 를 직접 주입한다. */
    ApprovalLineAuthorizeClient(
            RestClient restClient,
            InternalAuthProperties internalAuthProperties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.internalAuthProperties = internalAuthProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 결재라인 action 수행자가 결재자 집합에 포함되는지 조회한다.
     *
     * @param documentType 문서 종류
     * @param actionKey    액션 앵커
     * @param userId       수행자 계정 UUID
     * @return configured/allowed 인가 결과
     */
    public ApprovalLineAuthorizeResult authorize(String documentType, String actionKey, UUID userId) {
        String token = internalAuthProperties.getToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "결재라인 인가용 internal token 이 설정되지 않았습니다");
        }
        try {
            String body = restClient.post()
                    .uri("/auth/internal/approval-line/authorize")
                    .header(INTERNAL_TOKEN_HEADER, token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "documentType", documentType,
                            "actionKey", actionKey,
                            "userId", userId))
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("ApprovalLineAuthorizeClient.authorize 호출 실패 — documentType={}, actionKey={}, msg={}",
                    documentType, actionKey, ex.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "결재라인 인가 서버 호출에 실패했습니다");
        }
    }

    private ApprovalLineAuthorizeResult parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false) || !root.hasNonNull("data")) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "결재라인 인가 응답 형식 오류");
            }
            JsonNode data = root.get("data");
            return new ApprovalLineAuthorizeResult(
                    data.path("configured").asBoolean(false),
                    data.path("allowed").asBoolean(false));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "결재라인 인가 응답 형식 오류");
        }
    }
}
