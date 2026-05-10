package com.samhanair.logis.slip.editrequest.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 거절 요청 본문 — PR-H3. reason 은 필수 (≤500자).
 *
 * @param reason 거절 사유 (필수)
 */
public record RejectRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
