package com.samhanair.logis.product.web.dto;

import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 목록/조회 batch 용 경량 응답 — 출고가만 노출 (납품가 제외).
 *
 * <p>2026-05-22 Sprint 3: 안전재고 알림 등 사용자 노출 화면이 UUID 대신
 * productCode/modelName 비즈니스 식별자를 표시할 수 있도록 productCode field 추가.
 */
public record ProductSummaryResponse(
        UUID id,
        String name,
        String modelName,
        String productCode,
        UUID categoryId,
        BigDecimal sellingPrice,
        ProductStatus status) {

    /**
     * Backward-compatible 생성자 — productCode 미지원 기존 test 호환.
     */
    public ProductSummaryResponse(UUID id, String name, String modelName, UUID categoryId,
                                  BigDecimal sellingPrice, ProductStatus status) {
        this(id, name, modelName, null, categoryId, sellingPrice, status);
    }

    public static ProductSummaryResponse from(Product p) {
        return new ProductSummaryResponse(
                p.getId(),
                p.getName(),
                p.getModelName(),
                p.getProductCode(),
                p.getCategory().getId(),
                p.getSellingPrice(),
                p.getStatus());
    }
}
