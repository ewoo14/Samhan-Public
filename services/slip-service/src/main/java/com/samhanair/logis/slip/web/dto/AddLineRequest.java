package com.samhanair.logis.slip.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** 라인 추가 요청 — DRAFT/SAVED 단계에서만 허용. */
public record AddLineRequest(
        @NotNull UUID productId,
        @Size(max = 200) String productName,
        @Size(max = 100) String modelName,
        @NotNull @Positive Integer quantity,
        @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
        @Size(max = 200) String note) {
}
