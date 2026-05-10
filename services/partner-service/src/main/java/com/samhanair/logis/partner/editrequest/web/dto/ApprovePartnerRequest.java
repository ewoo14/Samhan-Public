package com.samhanair.logis.partner.editrequest.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 거래처 수정 요청 수락 body DTO — PR-H4b.
 */
public record ApprovePartnerRequest(
        @Size(max = 500) String note
) {
}
