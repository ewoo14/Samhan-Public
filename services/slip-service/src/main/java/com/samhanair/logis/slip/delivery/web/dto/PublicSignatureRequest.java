package com.samhanair.logis.slip.delivery.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 공개 모바일 서명 등록 요청 — Slice C (signature-slice-C Plan §2 + design mobile-spec.md §2.1).
 *
 * <p>POST {@code /public/batches/{token}/slips/{slipNo}/signature} body.
 *
 * @param signerName 인수자명 (1~50자, 필수)
 * @param signaturePngBase64 PNG data URI (예: {@code data:image/png;base64,iVBORw0...}) 또는 raw base64
 * @param clientHash Web Crypto API 로 클라이언트가 계산한 SHA-256 hex 64자 — 서버 재계산값과 비교 검증
 */
public record PublicSignatureRequest(
        @NotBlank(message = "signerName 은 필수입니다")
        @Size(min = 1, max = 50, message = "signerName 은 1~50자입니다")
        String signerName,
        @NotBlank(message = "signaturePngBase64 는 필수입니다")
        String signaturePngBase64,
        @NotBlank(message = "clientHash 는 필수입니다")
        @Size(min = 64, max = 64, message = "clientHash 는 SHA-256 hex 64자입니다")
        String clientHash) {
}
