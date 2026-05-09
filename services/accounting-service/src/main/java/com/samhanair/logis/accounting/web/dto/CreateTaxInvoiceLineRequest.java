package com.samhanair.logis.accounting.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 세금계산서 라인 1건 생성/수정 요청.
 * supplyAmount/vatAmount 는 자동 계산 (수량*단가 / 공급가액*0.1) — DTO 미노출.
 */
public record CreateTaxInvoiceLineRequest(
        @NotBlank(message = "itemName 은 필수입니다")
        @Size(max = 200, message = "itemName 은 최대 200자입니다")
        String itemName,

        @Size(max = 100, message = "spec 은 최대 100자입니다")
        String spec,

        @NotNull(message = "quantity 는 필수입니다")
        @DecimalMin(value = "0", message = "quantity 는 0 이상이어야 합니다")
        BigDecimal quantity,

        @NotNull(message = "unitPrice 는 필수입니다")
        @DecimalMin(value = "0", message = "unitPrice 는 0 이상이어야 합니다")
        BigDecimal unitPrice,

        @Size(max = 500, message = "memo 는 최대 500자입니다")
        String memo
) {}
