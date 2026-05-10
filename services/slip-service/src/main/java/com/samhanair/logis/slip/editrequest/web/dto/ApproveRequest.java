package com.samhanair.logis.slip.editrequest.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 수락 요청 본문 — PR-H3. note 는 선택 (수락 시점 메모).
 *
 * @param note 수락 메모 (선택, ≤500자)
 */
public record ApproveRequest(
        @Size(max = 500) String note
) {
}
