package com.samhanair.logis.product.web.dto;

import java.util.UUID;

/**
 * 품목별 F1-b 분류/고정DC 저장 요청.
 *
 * <p>FE 계약: {@code PATCH /api/v1/products/{modelCode}/classification}
 * body = {@code {catLId, catMId, catSId, fixedDiscountRate}}.
 * {@code fixedDiscountRate} 는 0~100 percent 값을 문자열 또는 null 로 전달한다.
 * 시트 sync 의 "0.5" → 50 보정과 달리 PATCH 는 사용자 입력 percent 정수/소수 계약을 그대로 저장한다.
 */
public record UpdateProductClassificationRequest(
        UUID catLId,
        UUID catMId,
        UUID catSId,
        String fixedDiscountRate) {
}
