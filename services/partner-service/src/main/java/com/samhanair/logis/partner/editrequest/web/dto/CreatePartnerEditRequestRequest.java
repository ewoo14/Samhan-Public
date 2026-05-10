package com.samhanair.logis.partner.editrequest.web.dto;

import com.samhanair.logis.shared.realtime.editrequest.EditRequestType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 거래처 entity 수정/삭제 요청 생성 DTO — PR-H4b.
 *
 * @param type EDIT / DELETE (필수)
 * @param reason 요청 사유 (선택, ≤500자)
 */
public record CreatePartnerEditRequestRequest(
        @NotNull EditRequestType type,
        @Size(max = 500) String reason
) {
}
