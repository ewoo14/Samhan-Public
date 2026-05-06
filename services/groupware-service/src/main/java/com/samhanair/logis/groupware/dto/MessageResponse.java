package com.samhanair.logis.groupware.dto;

import com.samhanair.logis.groupware.domain.Message;
import com.samhanair.logis.groupware.domain.MessageStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 메신저 단건 응답 DTO. inbox / 발송 응답 공용.
 *
 * @param messageId 메신저 식별자
 * @param senderId 송신자
 * @param recipientId 수신자
 * @param body 본문
 * @param status 읽음 여부
 * @param sentAt 발송 시각
 * @param readAt 열람 시각 (READ 만 의미)
 */
public record MessageResponse(
        UUID messageId,
        UUID senderId,
        UUID recipientId,
        String body,
        MessageStatus status,
        LocalDateTime sentAt,
        LocalDateTime readAt
) {

    public static MessageResponse from(Message m) {
        return new MessageResponse(m.getId(), m.getSenderId(), m.getRecipientId(), m.getBody(),
                m.getStatus(), m.getSentAt(), m.getReadAt());
    }
}
