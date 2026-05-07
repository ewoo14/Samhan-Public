package com.samhanair.logis.user.client;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.common.security.Role;
import com.samhanair.logis.security.InternalAuthProperties;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Internal-token-authenticated client to {@code auth-service}'s {@code /auth/internal/accounts}
 * endpoints. All methods throw {@link BusinessException} on non-2xx — {@link ErrorCode#CONFLICT}
 * for 409 (duplicate loginId) and {@link ErrorCode#INTERNAL_ERROR} for everything else, so the
 * provisioning saga can decide whether to compensate.
 */
@Component
public class AuthClient {

    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String AUTH_SERVICE_BASE = "http://auth-service";

    private final RestClient restClient;
    private final InternalAuthProperties properties;

    public AuthClient(RestClient.Builder loadBalancedRestClientBuilder, InternalAuthProperties properties) {
        this.properties = properties;
        this.restClient = loadBalancedRestClientBuilder
                .baseUrl(AUTH_SERVICE_BASE)
                .build();
    }

    public void createAccount(UUID id, String loginId, String password, String displayName, Role role) {
        Map<String, Object> body = Map.of(
                "id", id.toString(),
                "loginId", loginId,
                "password", password,
                "displayName", displayName,
                "role", role.name());
        execute("POST createAccount", () -> restClient.post()
                .uri("/auth/internal/accounts")
                .header(INTERNAL_TOKEN_HEADER, requireToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                    if (res.getStatusCode().value() == 409) {
                        throw new BusinessException(ErrorCode.CONFLICT, "이미 사용중인 아이디입니다");
                    }
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "auth-service 4xx: " + res.getStatusCode());
                })
                .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "auth-service 5xx: " + res.getStatusCode());
                })
                .toBodilessEntity());
    }

    public void updateRole(UUID id, Role role) {
        execute("PATCH updateRole", () -> restClient.patch()
                .uri("/auth/internal/accounts/{id}/role", id)
                .header(INTERNAL_TOKEN_HEADER, requireToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("role", role.name()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "auth-service updateRole failed: " + res.getStatusCode());
                })
                .toBodilessEntity());
    }

    public void updateDisplayName(UUID id, String displayName) {
        execute("PATCH updateDisplayName", () -> restClient.patch()
                .uri("/auth/internal/accounts/{id}/display-name", id)
                .header(INTERNAL_TOKEN_HEADER, requireToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("displayName", displayName))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "auth-service updateDisplayName failed: " + res.getStatusCode());
                })
                .toBodilessEntity());
    }

    public void disable(UUID id) {
        execute("PATCH disable", () -> restClient.patch()
                .uri("/auth/internal/accounts/{id}/disable", id)
                .header(INTERNAL_TOKEN_HEADER, requireToken())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "auth-service disable failed: " + res.getStatusCode());
                })
                .toBodilessEntity());
    }

    public void delete(UUID id) {
        execute("DELETE compensation", () -> restClient.delete()
                .uri("/auth/internal/accounts/{id}", id)
                .header(INTERNAL_TOKEN_HEADER, requireToken())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "auth-service delete failed: " + res.getStatusCode());
                })
                .toBodilessEntity());
    }

    private String requireToken() {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "app.security.internal.token 미설정");
        }
        return token;
    }

    private void execute(String label, Runnable call) {
        try {
            call.run();
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("AuthClient {} failed: {}", label, ex.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "auth-service 호출 실패: " + label, ex);
        }
    }
}
