package com.samhanair.logis.product.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 모델명 단건 조회 요청 — slip-service 의 ProductClient.lookupByModel 이 사용.
 * Slip 출력 슬라이스의 modelName onBlur lookup 흐름 (개발책임자 Q2=A 결정).
 *
 * @param modelName 정확 매칭할 제품 모델명. 공백 trim 후 사용. 1~100자.
 */
public record LookupByModelRequest(
        @NotBlank(message = "modelName은 필수입니다")
        @Size(max = 100, message = "modelName은 최대 100자입니다")
        String modelName) {
}
