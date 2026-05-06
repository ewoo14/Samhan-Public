package com.samhanair.logis.notification.adapter.email;

import com.samhanair.logis.notification.adapter.NotificationGatewayResult;
import com.samhanair.logis.notification.domain.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AWS SES Email 어댑터 — Phase 10 cutover 시점 활성. 본 W3 시점은 placeholder 로 모든 호출
 * stub-success (외부 SES 호출 X).
 *
 * <p>Phase 10 활성 시점에 AWS SDK ses.SendEmailCommand 통합 + bounce / complaint webhook 처리.
 */
@Slf4j
@Component
public class SesEmailAdapter implements EmailAdapter {

    @Override
    public NotificationGatewayResult send(NotificationRequest request) {
        // Phase 10 cutover 시 ses.send-email 호출 자리.
        String stubId = "ses-stub-" + request.getId();
        log.debug("[SesEmailAdapter] SES placeholder — Phase 10 활성 시점 SDK 호출 자리. requestId={}",
                request.getId());
        return NotificationGatewayResult.success(stubId, "{\"note\":\"SES stub (Phase 10 활성)\"}");
    }
}
