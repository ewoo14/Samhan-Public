package com.samhanair.logis.notification.dto;

import com.samhanair.logis.notification.domain.NotificationChannel;
import com.samhanair.logis.notification.domain.RecipientType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 발송 요청 DTO — Internal + Admin 양쪽 endpoint 공용.
 *
 * <p>EXTERNAL_PHONE 인 경우 recipientAddress (전화번호) 필수, USER/PARTNER 인 경우 recipientId 필수.
 *
 * <p>post-W5 backlog cleanup (Q-W3-2, D-P9-21) — payload 에 {@link Size}(max=4000) 추가.
 * Postgres TOAST (TOAST 임계 ~ 2KB 압축, 4KB 비압축) 회피용 + 비정상 페이로드 입력 차단.
 *
 * @param recipientType USER / PARTNER / EXTERNAL_PHONE
 * @param recipientId USER / PARTNER UUID (EXTERNAL_PHONE 인 경우 null)
 * @param recipientAddress EXTERNAL_PHONE 의 전화번호 또는 보조 채널 주소
 * @param channel PUSH / EMAIL / SMS
 * @param templateCode 사전 등록 템플릿 코드 (선택)
 * @param subject 제목 (이메일 / push)
 * @param body 본문
 * @param payload 부가 메타 (JSON 문자열, 선택, max 4000 byte)
 */
public record NotificationSendRequest(
        @NotNull RecipientType recipientType,
        UUID recipientId,
        @Size(max = 200) String recipientAddress,
        @NotNull NotificationChannel channel,
        @Size(max = 50) String templateCode,
        @Size(max = 200) String subject,
        @Size(max = 2000) String body,
        @Size(max = 4000, message = "payload size 는 4000 byte 이하만 허용 (Postgres TOAST 임계 회피)")
        String payload
) {
}
