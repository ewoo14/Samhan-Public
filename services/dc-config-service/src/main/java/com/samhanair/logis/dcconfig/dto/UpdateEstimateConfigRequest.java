package com.samhanair.logis.dcconfig.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** 종합견적서 전역 가격 파라미터 수정 요청. */
public record UpdateEstimateConfigRequest(
        @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal commonHomeDiscountRate,
        @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal commonCommercialDiscountRate,
        @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal oldProductDiscountRate,
        @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal vatRate,
        @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal cardFeeRate,
        @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal advanceDiscountRate,
        @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal comboWarnRate,
        String footerNotice
) {
}
