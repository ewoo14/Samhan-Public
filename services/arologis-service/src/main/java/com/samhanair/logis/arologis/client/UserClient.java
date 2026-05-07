package com.samhanair.logis.arologis.client;

import com.samhanair.logis.userclient.DefaultUserVerifier;
import com.samhanair.logis.userclient.UserVerifier;
import com.samhanair.logis.userclient.UserVerifierProperties;
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
 *
 * <p>BE-3 잔존 fix — `shared:user-client-abstraction` 의 {@link UserVerifier} 5번째 소비자
 * 표기 정합성 활성. {@link #exists(UUID)} 경로는 {@link DefaultUserVerifier} 위임 (cache +
 * RestClient + 4xx/5xx 정책 notification / groupware / dashboard / partner 와 1:1 일관).
 * skeleton-mode 시점에는 검증 통과 (true) — W10-1 Mock 매칭 흐름 보존. W10-3 driver-app 인증
 * 시점에 `samhan.arologis.client.skeleton-mode=false` 전환 → 실 RPC 활성.
 */
@Slf4j
@Component
public class UserClient {

    private final RestClient.Builder builder;
    private final String baseUrl;
    private final String internalToken;
    private final boolean skeletonMode;
    private final UserVerifier userVerifier;

    public UserClient(RestClient.Builder builder,
                      @Value("${samhan.user-service.url:http://localhost:8083}") String baseUrl,
                      @Value("${app.security.internal.token:}") String internalToken,
                      @Value("${samhan.arologis.client.skeleton-mode:true}") boolean skeletonMode) {
        this.builder = builder;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.skeletonMode = skeletonMode;

        // BE-3 채택 fix — UserVerifier 5번째 소비자 활성 (notification + groupware + dashboard
        // + partner + arologis = 5). notification UserClient 패턴 1:1 일관 — DefaultUserVerifier
        // self-construct (외부 Bean 의존 회피, IT @MockBean UserClient 패턴 보존).
        UserVerifierProperties props = new UserVerifierProperties();
        props.setBaseUrl(baseUrl);
        props.setInternalToken(internalToken);
        // skeleton 단계 fail-soft 기본 (W10-3 진입 시점 fail-fast 토글 검토).
        props.setFailFast(false);
        this.userVerifier = new DefaultUserVerifier(builder, props);
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

    /**
     * 사용자 존재 검증 — {@link UserVerifier} 위임 wrapper (BE-3 채택 fix).
     *
     * <p>skeleton-mode 일 때 검증 통과 (true) — W10-1 Mock 매칭 흐름 보존. 비-skeleton 일 때
     * UserVerifier 의 cache + fail-fast/fail-soft 정책 그대로 활용 (notification + groupware +
     * dashboard + partner 와 동일 정책).
     *
     * @param userId user UUID (null → false)
     * @return 존재 시 true, 미존재 시 false. skeleton-mode true 시 항상 true (검증 통과).
     */
    public boolean exists(UUID userId) {
        if (userId == null) {
            return false;
        }
        if (skeletonMode) {
            return true;
        }
        return userVerifier.exists(userId);
    }

    public record UserSummary(UUID userId, String displayName) {}
}
