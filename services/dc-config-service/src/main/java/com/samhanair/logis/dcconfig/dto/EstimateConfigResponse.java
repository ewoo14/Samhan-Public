package com.samhanair.logis.dcconfig.dto;

import com.samhanair.logis.dcconfig.domain.EstimateConfig;
import java.math.BigDecimal;

/** 종합견적서 전역 가격 파라미터 응답. */
public record EstimateConfigResponse(
        BigDecimal commonHomeDiscountRate,
        BigDecimal commonCommercialDiscountRate,
        BigDecimal oldProductDiscountRate,
        BigDecimal vatRate,
        BigDecimal cardFeeRate,
        BigDecimal advanceDiscountRate,
        BigDecimal comboWarnRate,
        String footerNotice
) {

    public static EstimateConfigResponse from(EstimateConfig config) {
        return new EstimateConfigResponse(
                config.getCommonHomeDiscountRate(),
                config.getCommonCommercialDiscountRate(),
                config.getOldProductDiscountRate(),
                config.getVatRate(),
                config.getCardFeeRate(),
                config.getAdvanceDiscountRate(),
                config.getComboWarnRate(),
                config.getFooterNotice()
        );
    }
}
