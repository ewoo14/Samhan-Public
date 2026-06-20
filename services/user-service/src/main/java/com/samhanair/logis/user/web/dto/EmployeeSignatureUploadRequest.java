package com.samhanair.logis.user.web.dto;

import com.samhanair.logis.user.domain.SignatureChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 사원 서명 업로드/등록 요청 - C1a. PATCH /api/v1/admin/users/{id}/signature body.
 *
 * @param signaturePngBase64 PNG base64 (data URI 또는 raw, 서비스가 디코드)
 * @param signatureHash 클라 계산 SHA-256 hex 64자 (서버 재검증, 불일치 400)
 * @param channel 입력 채널 (MOBILE_CANVAS / UPLOAD)
 */
public record EmployeeSignatureUploadRequest(
        @NotBlank(message = "signaturePngBase64 는 필수입니다")
        @Size(max = 90000, message = "서명 base64 입력이 너무 큽니다 (최대 약 50KB PNG)")
        String signaturePngBase64,
        @NotBlank(message = "signatureHash 는 필수입니다")
        @Size(min = 64, max = 64, message = "signatureHash 는 SHA-256 hex 64자여야 합니다")
        String signatureHash,
        @NotNull(message = "channel 은 필수입니다") SignatureChannel channel
) {
}
