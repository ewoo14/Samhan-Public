package com.samhanair.logis.product.web.dto;

import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** 제품 단건 상세 응답 — BaseEntity 의 audit 필드까지 노출. */
public record ProductResponse(
        UUID id,
        String name,
        String modelName,
        UUID categoryId,
        String categoryName,
        BigDecimal sellingPrice,
        BigDecimal purchasePrice,
        String currency,
        ProductStatus status,
        Map<String, String> tags,
        String description,
        LocalDateTime createdAt,
        String createdBy,
        LocalDateTime modifiedAt,
        String modifiedBy) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getModelName(),
                p.getCategory().getId(),
                p.getCategory().getName(),
                p.getSellingPrice(),
                p.getPurchasePrice(),
                p.getCurrency(),
                p.getStatus(),
                p.getTags(),
                p.getDescription(),
                p.getCreatedAt(),
                p.getCreatedBy(),
                p.getModifiedAt(),
                p.getModifiedBy());
    }
}
