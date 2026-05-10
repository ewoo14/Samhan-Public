package com.samhanair.logis.accounting.editrequest.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 회계 수정 요청 수락 body DTO — PR-H4b.
 *
 * @param note 수락 메모 (선택, ≤500자, decision_reason 컬럼에 저장)
 */
public record ApproveAccountingRequest(
        @Size(max = 500) String note
) {
}
