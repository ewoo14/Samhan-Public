package com.samhanair.logis.slip.delivery.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공개 모바일 배송기사 서명 등록 요청 — Slice C2 (PR #23 follow-up).
 *
 * <p>POST {@code /public/batches/{token}/slips/{slipNo}/driver-signature} body.
 *
 * <p>인수자 서명({@link PublicSignatureRequest})과 다른 점: signerName 별도 입력 X
 * (Slip.driverName 재사용). PNG + hash 만 입력.
 */
public record PublicDriverSignatureRequest(
        @NotBlank(message = "signaturePngBase64 는 필수입니다")
        String signaturePngBase64,
        @NotBlank(message = "clientHash 는 필수입니다")
        @Size(min = 64, max = 64, message = "clientHash 는 SHA-256 hex 64자입니다")
        String clientHash) {
}
