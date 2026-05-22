package com.samhanair.logis.product.web.dto;

import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** 목록/조회 batch 용 경량 응답 — 출고가만 노출 (납품가 제외). */
public record ProductSummaryResponse(
        UUID id,
        String name,
        String modelName,
        UUID categoryId,
        BigDecimal sellingPrice,
        ProductStatus status) {

    public static ProductSummaryResponse from(Product p) {
        return new ProductSummaryResponse(
                p.getId(),
                p.getName(),
                p.getModelName(),
                p.getCategory().getId(),
                p.getSellingPrice(),
                p.getStatus());
    }
}
