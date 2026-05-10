package com.samhanair.logis.partner.editrequest.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 거래처 수정 요청 거절 body DTO — PR-H4b.
 *
 * @param reason 거절 사유 (필수, 1~500자)
 */
public record RejectPartnerRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
