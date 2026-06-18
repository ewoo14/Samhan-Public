package com.samhanair.logis.product.web.dto;

import java.util.UUID;

/**
 * 품목별 F1-b 분류 저장 요청.
 *
 * <p>FE 계약: {@code PATCH /api/v1/products/{modelCode}/classification}
 * body = {@code {catLId, catMId, catSId}}.
 * 고정DC율은 인라인 자동저장 전용 {@code PATCH /api/v1/products/{modelCode}/fixed-discount} 로 분리한다.
 */
public record UpdateProductClassificationRequest(
        UUID catLId,
        UUID catMId,
        UUID catSId) {
}
