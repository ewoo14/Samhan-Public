package com.samhanair.logis.product.web.dto;

/**
 * 품목별 고정DC율 인라인 자동저장 요청.
 *
 * <p>FE 계약: {@code PATCH /api/v1/products/{modelCode}/fixed-discount}
 * body = {@code {fixedDiscountRate}}. 값은 0~100 percent 문자열 또는 null 이며,
 * null 은 빈칸 저장(전역DC율 영향 품목)을 뜻한다.
 */
public record UpdateProductFixedDiscountRequest(String fixedDiscountRate) {
}
