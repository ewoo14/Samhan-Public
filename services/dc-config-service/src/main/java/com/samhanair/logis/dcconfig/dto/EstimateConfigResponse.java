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
        Boolean homeNoHose,
        Boolean homeNoBranch,
        Boolean homeWithFoot,
        String homeDefaultPanel,
        String singleDefaultWiredRemote,
        Boolean singleNoRemote,
        Boolean singleWithBase,
        String singleDefaultPanel,
        String singlePanelShape,
        BigDecimal singleDiscount,
        BigDecimal singleOneWayDiscount,
        String singleMaterialInclusion,
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
                config.getHomeNoHose(),
                config.getHomeNoBranch(),
                config.getHomeWithFoot(),
                config.getHomeDefaultPanel(),
                config.getSingleDefaultWiredRemote(),
                config.getSingleNoRemote(),
                config.getSingleWithBase(),
                config.getSingleDefaultPanel(),
                config.getSinglePanelShape(),
                config.getSingleDiscount(),
                config.getSingleOneWayDiscount(),
                config.getSingleMaterialInclusion(),
                config.getFooterNotice()
        );
    }
}
