package com.samhanair.logis.arologis.client;

import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * notification-service (8093, W3) 호출 client — Phase 10 W10-1 arologis-service.
 *
 * <p>배차 완료 / 기사 매칭 알림. skeleton-mode 기본값 — 실 호출은 자동 매칭 단계 (admin trigger).
 *
 * <p>notification-service `POST /internal/notifications/send` (W3 endpoint) 활용.
 */
@Slf4j
@Component
public class NotificationClient {

    private final RestClient.Builder builder;
    private final String baseUrl;
    private final String internalToken;
    private final boolean skeletonMode;

    public NotificationClient(RestClient.Builder builder,
                              @Value("${samhan.notification-service.url:http://localhost:8093}") String baseUrl,
                              @Value("${app.security.internal.token:}") String internalToken,
                              @Value("${samhan.arologis.client.skeleton-mode:true}") boolean skeletonMode) {
        this.builder = builder;
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
        this.skeletonMode = skeletonMode;
    }

    /**
     * 배차 완료 / 기사 매칭 알림 발송. skeleton-mode 시 외부 호출 회피 + true 반환 (silent success).
     *
     * @param recipientUserId 수신자 user-service userId
     * @param channel 채널 ("PUSH" / "SMS" / "EMAIL")
     * @param subject 제목
     * @param body 본문
     * @return 발송 trigger 성공 여부. fail-soft — skeleton 또는 예외 시 false (호출 측 무시 가능)
     */
    public boolean send(UUID recipientUserId, String channel, String subject, String body) {
        if (skeletonMode) {
            log.debug("NotificationClient.send skeleton-mode — userId={} channel={} subject={} (외부 호출 회피)",
                    recipientUserId, channel, subject);
            return true;
        }
        try {
            RestClient client = builder.baseUrl(baseUrl).build();
            client.post()
                    .uri("/internal/notifications/send")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "recipientUserId", recipientUserId,
                            "channel", channel,
                            "subject", subject == null ? "" : subject,
                            "body", body == null ? "" : body))
                    .retrieve()
                    .body(String.class);
            return true;
        } catch (Exception ex) {
            log.warn("NotificationClient.send 실패 — userId={}, msg={}", recipientUserId, ex.getMessage());
            return false;
        }
    }
}
