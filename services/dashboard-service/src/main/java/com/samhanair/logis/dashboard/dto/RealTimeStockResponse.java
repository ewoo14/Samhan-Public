package com.samhanair.logis.dashboard.dto;

import com.samhanair.logis.dashboard.domain.RealTimeStock;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 실시간 재고 응답 DTO — admin 화면 노출.
 *
 * <p>UUID 비공개 가드 — productId (UUID) 노출 X. productCode (사용자 노출 식별자) 만 노출.
 * 본 응답은 admin / 형제 service 한정 (사용자 화면 직접 노출 시 productCode 매핑 의무).
 */
public record RealTimeStockResponse(
        String productCode,
        String warehouseCode,
        BigDecimal quantity,
        LocalDateTime refreshedAt
) {

    /**
     * RealTimeStock 엔티티 → response. productCode 는 dashboard 가 보유하지 않으므로 (UUID 만 보유)
     * skeleton 단계에서는 호출자가 별도 매핑 후 부여 (Phase 10 시점 inventory-service lookup 통합).
     */
    public static RealTimeStockResponse from(RealTimeStock stock, String productCode) {
        return new RealTimeStockResponse(
                productCode,
                stock.getWarehouseCode(),
                stock.getQuantity(),
                stock.getRefreshedAt());
    }
}
