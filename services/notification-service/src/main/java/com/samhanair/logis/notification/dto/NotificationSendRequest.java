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
 * @param recipientType USER / PARTNER / EXTERNAL_PHONE
 * @param recipientId USER / PARTNER UUID (EXTERNAL_PHONE 인 경우 null)
 * @param recipientAddress EXTERNAL_PHONE 의 전화번호 또는 보조 채널 주소
 * @param channel PUSH / EMAIL / SMS
 * @param templateCode 사전 등록 템플릿 코드 (선택)
 * @param subject 제목 (이메일 / push)
 * @param body 본문
 * @param payload 부가 메타 (JSON 문자열, 선택)
 */
public record NotificationSendRequest(
        @NotNull RecipientType recipientType,
        UUID recipientId,
        @Size(max = 200) String recipientAddress,
        @NotNull NotificationChannel channel,
        @Size(max = 50) String templateCode,
        @Size(max = 200) String subject,
        @Size(max = 2000) String body,
        String payload
) {
}
