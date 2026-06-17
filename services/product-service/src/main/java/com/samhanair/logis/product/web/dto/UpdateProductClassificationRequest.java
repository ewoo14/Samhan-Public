package com.samhanair.logis.product.web.dto;

import java.util.UUID;

/**
 * 품목별 F1-b 분류/고정DC 저장 요청.
 *
 * <p>FE 계약: {@code PATCH /api/v1/products/{modelCode}/classification}
 * body = {@code {catLId, catMId, catSId, fixedDiscountRate}}.
 * {@code fixedDiscountRate} 는 Electron 입력값 그대로 문자열 또는 null 로 전달된다.
 */
public record UpdateProductClassificationRequest(
        UUID catLId,
        UUID catMId,
        UUID catSId,
        String fixedDiscountRate) {
}
