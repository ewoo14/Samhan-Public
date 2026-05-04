package com.samhanair.logis.inventory.web.dto;

import java.util.UUID;

/** reserve / release 결과 응답 — 갱신된 잔량과 actor. */
public record ReservationResponse(
        UUID productId,
        UUID warehouseId,
        int quantity,
        int availableQty,
        int reservedQty,
        String actorUserId) {
}
