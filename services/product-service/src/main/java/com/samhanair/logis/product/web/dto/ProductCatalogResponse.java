package com.samhanair.logis.product.web.dto;

import com.samhanair.logis.product.domain.EstimateCategory;
import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.UsageScope;
import java.math.BigDecimal;

/**
 * 카탈로그 endpoint 응답 — UUID 비공개 원칙 (feedback_uuid_no_user_visibility.md) 충족.
 * 사용자 화면에는 modelCode (사용자 노출 식별자) 만 노출, internal id (UUID) 미노출.
 */
public record ProductCatalogResponse(
        String modelCode,
        String name,
        UsageScope usageScope,
        EstimateCategory estimateCategory,
        BigDecimal releasePrice,
        BigDecimal deliveryPrice,
        boolean hasVariableDiscount,
        boolean legacyDiscountFlag,
        String discountFlags
) {
    public static ProductCatalogResponse from(Product p) {
        return new ProductCatalogResponse(
                p.getModelCode() == null ? p.getModelName() : p.getModelCode(),
                p.getName(),
                p.getUsageScope(),
                p.getEstimateCategory(),
                p.getReleasePrice(),
                p.getDeliveryPrice(),
                Boolean.TRUE.equals(p.getHasVariableDiscount()),
                Boolean.TRUE.equals(p.getLegacyDiscountFlag()),
                p.getDiscountFlags()
        );
    }
}
