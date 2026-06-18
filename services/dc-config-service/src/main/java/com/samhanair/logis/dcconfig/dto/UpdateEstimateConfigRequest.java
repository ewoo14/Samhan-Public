package com.samhanair.logis.dcconfig.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
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
        Boolean homeNoHose,
        Boolean homeNoBranch,
        Boolean homeWithFoot,
        @Size(max = 64) String homeDefaultPanel,
        @Size(max = 64) String singleDefaultWiredRemote,
        Boolean singleNoRemote,
        Boolean singleWithBase,
        @Size(max = 64) String singleDefaultPanel,
        @Size(max = 16) String singlePanelShape,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal singleDiscount,
        @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal singleOneWayDiscount,
        @Size(max = 16) String singleMaterialInclusion,
        String footerNotice
) {
}
