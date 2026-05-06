package com.samhanair.logis.notification.adapter.push;

import com.samhanair.logis.notification.adapter.NotificationGatewayResult;
import com.samhanair.logis.notification.config.FcmProperties;
import com.samhanair.logis.notification.domain.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * FCM (Firebase Cloud Messaging) 어댑터 — Phase 9 W3 placeholder.
 *
 * <p>본 슬라이스 시점에는 실제 FCM SDK 호출은 비활성 (skeleton). credentials 가 dev placeholder
 * 인 경우 즉시 success 반환 (로컬 dev / dev-default 환경 지원). Phase 10 cutover 또는 모바일
 * staff app 활성 시점에 FCM Admin SDK 통합 + retry-after / topic 발송 등 본격 통합.
 *
 * <p>{@link FcmProperties#getCredentialsPath()} 가 placeholder 인 경우 외부 호출은 skip 하고
 * messageId 는 fcm-stub-{requestId} 로 발급. 운영 시 ConditionalOnProperty 로 토글 가능
 * ({@code samhan.notification.fcm.enabled=true}).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "samhan.notification.fcm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FcmPushAdapter implements PushAdapter {

    private final FcmProperties properties;

    public FcmPushAdapter(FcmProperties properties) {
        this.properties = properties;
    }

    @Override
    public NotificationGatewayResult send(NotificationRequest request) {
        try {
            String credPath = properties.getCredentialsPath();
            if (credPath == null || credPath.isBlank() || "CHANGE_ME_LOCAL_ONLY".equals(credPath)) {
                // Phase 10 cutover 전 skeleton — 실제 FCM 호출 skip + dev success.
                String stubId = "fcm-stub-" + request.getId();
                log.debug("[FcmPushAdapter] FCM credentials placeholder — stub success requestId={} stubId={}",
                        request.getId(), stubId);
                return NotificationGatewayResult.success(stubId, "{\"note\":\"FCM stub (Phase 10 활성)\"}");
            }
            // Phase 10: FirebaseMessaging.getInstance().send(Message.builder()...) 통합
            String stubId = "fcm-prod-stub-" + request.getId();
            log.info("[FcmPushAdapter] FCM 통합 placeholder — Phase 10 활성 시점 SDK 호출 자리. requestId={}",
                    request.getId());
            return NotificationGatewayResult.success(stubId, "{\"note\":\"FCM SDK placeholder\"}");
        } catch (Exception ex) {
            log.warn("[FcmPushAdapter] 호출 실패 requestId={} msg={}", request.getId(), ex.getMessage());
            return NotificationGatewayResult.failure("FAILURE_FCM", ex.getMessage());
        }
    }
}
