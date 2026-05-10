package com.samhanair.logis.slip.audit.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * audit overlay 단일 필드 patch 요청 DTO — PR-H2.
 *
 * @param fieldName 변경할 필드 식별자 (≤50자, 필수). 도메인 {@code Slip#applyOverlayPatch} 가 가드.
 * @param newValue 새 값 (null/empty 가능 — 필드 clear)
 */
public record OverlayPatchRequest(
        @NotBlank @Size(max = 50) String fieldName,
        String newValue
) {
}
