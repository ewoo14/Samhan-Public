package com.samhanair.logis.slip.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 전표 반려 요청 — 사유 필수. memo 앞에 prepend 된다. */
public record RejectRequest(
        @NotBlank @Size(max = 500) String reason) {
}
