package com.samhanair.logis.groupware.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 메신저 발송 요청 DTO.
 *
 * @param senderId 송신자 user UUID
 * @param recipientId 수신자 user UUID (송신자와 동일 시 거부)
 * @param body 본문
 */
public record MessageSendRequest(
        @NotNull UUID senderId,
        @NotNull UUID recipientId,
        @NotBlank @Size(max = 2000) String body
) {
}
