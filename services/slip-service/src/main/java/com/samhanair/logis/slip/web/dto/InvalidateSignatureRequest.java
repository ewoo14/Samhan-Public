package com.samhanair.logis.slip.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관리자 서명 무효화 요청 — Slice C (signature-slice-C Plan §2).
 *
 * <p>{@code DELETE /api/slips/{id}/signature} body. MASTER 권한 필수.
 *
 * @param reason 무효화 사유 (필수, ≤500자) — audit log 에 그대로 저장
 */
public record InvalidateSignatureRequest(
        @NotBlank(message = "reason 은 필수입니다")
        @Size(max = 500, message = "reason 은 최대 500자입니다")
        String reason) {
}
