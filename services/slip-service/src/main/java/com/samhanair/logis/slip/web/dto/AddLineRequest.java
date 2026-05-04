package com.samhanair.logis.slip.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 라인 추가 요청 — DRAFT/SAVED 단계에서만 허용.
 * Slice A (sales-polish-2): {@code specification} 필드 신규 추가 (사용자 피드백 #4).
 */
public record AddLineRequest(
        @NotNull UUID productId,
        @Size(max = 200) String productName,
        @Size(max = 100) String modelName,
        @Size(max = 50) String specification,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        @Size(max = 200) String note) {
}
