package com.samhanair.logis.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공개 모바일 서명 제출 요청 (slice C1b · contract · NO-AUTH 토큰 게이트).
 *
 * @param signaturePngBase64 canvas PNG (data URI 또는 raw base64)
 * @param signatureHash 클라 계산 SHA-256 hex (BE 재검증, 불일치 400)
 */
public record PublicEmployeeSignatureRequest(
        @NotBlank
        @Size(max = 90000, message = "서명 base64 입력이 너무 큽니다 (최대 약 50KB PNG)")
        String signaturePngBase64,
        @NotBlank
        @Size(min = 64, max = 64, message = "signatureHash 는 SHA-256 hex 64자여야 합니다")
        String signatureHash) {}
