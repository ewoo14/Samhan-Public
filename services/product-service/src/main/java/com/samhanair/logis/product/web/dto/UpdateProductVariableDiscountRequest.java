package com.samhanair.logis.product.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 품목 변동DC 수동 override 요청 DTO.
 *
 * <p>{@code PATCH /api/v1/products/{modelCode}/variable-discount} 엔드포인트 body.
 * {@code hasVariableDiscount} 는 필수이며, 저장 시 {@code variableDiscountManual=true} 로 보호된다.
 */
public record UpdateProductVariableDiscountRequest(
        @NotNull(message = "hasVariableDiscount 는 필수입니다")
        Boolean hasVariableDiscount) {
}
