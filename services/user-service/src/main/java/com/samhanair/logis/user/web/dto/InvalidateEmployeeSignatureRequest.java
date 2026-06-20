package com.samhanair.logis.user.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사원 서명 무효화 요청 - C1a. MASTER 한정. (계약상 DELETE 는 ?reason= 쿼리이나, 향후 body
 * 전환 대비 record 제공.)
 *
 * @param reason 무효화 사유 (필수, 500자 이하) - audit INVALIDATE 행에 저장
 */
public record InvalidateEmployeeSignatureRequest(
        @NotBlank(message = "reason 은 필수입니다")
        @Size(max = 500, message = "reason 은 최대 500자입니다")
        String reason) {
}
