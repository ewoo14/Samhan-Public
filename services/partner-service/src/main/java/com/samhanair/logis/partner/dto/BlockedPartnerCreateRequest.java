package com.samhanair.logis.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Phase 10 PR-D Part B — admin 단건 BLOCK 등록 요청.
 *
 * <p>partnerCode 직접 입력 패턴 — admin 화면에서 partnerCode 를 모를 경우 PartnerAdminController
 * 의 {@code GET /by-name} 으로 우선 lookup 후 본 endpoint 호출.
 *
 * @param partnerCode 차단 대상 partnerCode (필수)
 * @param blockReason 차단 사유 (선택)
 */
public record BlockedPartnerCreateRequest(
        @NotBlank @Size(max = 50) String partnerCode,
        @Size(max = 500) String blockReason
) {
}
