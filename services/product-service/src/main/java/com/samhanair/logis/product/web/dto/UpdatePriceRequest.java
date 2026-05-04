package com.samhanair.logis.product.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 가격 수정 — ACCOUNTANT 도 호출 가능한 유일한 mutation. null 필드는 미변경. */
public record UpdatePriceRequest(
        @DecimalMin("0.00") BigDecimal sellingPrice,
        @DecimalMin("0.00") BigDecimal purchasePrice,
        @Size(min = 3, max = 3) String currency) {
}
