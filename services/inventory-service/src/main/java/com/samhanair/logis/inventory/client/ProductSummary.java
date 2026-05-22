package com.samhanair.logis.inventory.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * product-service 가 반환하는 제품 요약. inventory-service 가 product 도메인을 직접
 * import 하지 않도록 wire-format 의 record 사본을 둔다 (status 는 String 문자열로).
 *
 * <p>2026-05-22 Sprint 3: 안전재고 알림 화면에서 사용자에게 productCode/modelName 표시를
 * 위해 productCode field 추가 — product-service ProductSummaryResponse 와 1:1 정합.
 */
public record ProductSummary(
        UUID id,
        String name,
        String modelName,
        String productCode,
        UUID categoryId,
        BigDecimal sellingPrice,
        String status) {

    /**
     * Backward-compatible 생성자 — productCode 미지원 호출자 (기존 14 test) 호환.
     * 2026-05-22 Sprint 3 productCode field 추가 후에도 기존 mock 사용처를 변경하지 않도록.
     */
    public ProductSummary(UUID id, String name, String modelName, UUID categoryId,
                          BigDecimal sellingPrice, String status) {
        this(id, name, modelName, null, categoryId, sellingPrice, status);
    }
}
