package com.samhanair.logis.arologis.client;

import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * user-service (8083) 호출 client — Phase 10 W10-1 arologis-service.
 *
 * <p>배차담당자 인증 + Driver-app 인증 (W10-3 활성). skeleton-mode 기본값 — 실 호출은
 * W10-3 시점에 RN Expo 어플 통합 단계.
 */
@Slf4j
@Component
public class UserClient {

    private final RestClient.Builder builder;
    private final String baseUrl;
    private final String internalToken;
    private final boolean skeletonMode;

    public UserClient(RestClient.Builder builder,
                      @Value("${samhan.user-service.url:http://localhost:8083}") String baseUrl,
                      @Value("${app.security.internal.token:}") String internalToken,
                      @Value("${samhan.arologis.client.skeleton-mode:true}") boolean skeletonMode) {
        this.builder = builder;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.skeletonMode = skeletonMode;
    }

    /**
     * userId 로 user-service lookup — userExists 여부만 반환 (skeleton).
     * 실 호출은 W10-3 driver-app 인증 시점.
     */
    public Optional<UserSummary> findById(UUID userId) {
        if (userId == null || skeletonMode) {
            return Optional.empty();
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            client.get()
                    .uri("/internal/users/{userId}", userId)
                    .header("X-Internal-Token", internalToken)
                    .retrieve()
                    .body(String.class);
            return Optional.of(new UserSummary(userId, "(W10-3 통합)"));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("UserClient lookup 예외 — userId={}, status={}", userId, ex.getStatusCode());
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("UserClient lookup 실패 — userId={}, msg={}", userId, ex.getMessage());
            return Optional.empty();
        }
    }

    public record UserSummary(UUID userId, String displayName) {}
}
