package com.samhanair.logis.inventory.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 이동전표 반려 요청 — 사유 필수. */
public record RejectRequest(
        @NotBlank @Size(max = 500) String reason) {
}
