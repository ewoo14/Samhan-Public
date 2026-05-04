package com.samhanair.logis.inventory.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 다중 productId 일괄 재고 잔량 조회 요청 — Sales Form Polish 슬라이스의 영업원 견적 단계
 * 다행 동시 조회용. 1 ~ 100건 사이로 제한 (product-service lookup 한도와 동일).
 *
 * @param productIds 조회할 제품 UUID 리스트 (1 ~ 100건)
 */
public record BatchBalanceRequest(
        @NotEmpty(message = "productIds 는 비어 있을 수 없습니다")
        @Size(max = 100, message = "한 번에 조회할 수 있는 최대 제품 수는 100건입니다")
        List<UUID> productIds) {
}
