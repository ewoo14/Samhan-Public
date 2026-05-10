package com.samhanair.logis.accounting.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * product-service 가 반환하는 제품 요약 (PR-E2 BE-A12 의존).
 *
 * <p>inventory-service 의 동일 record 를 답습한 wire-format 사본 — accounting-service 가
 * product 도메인을 직접 import 하지 않도록 격리. status 는 String 으로 유지.
 *
 * <p>BE-A12 일별 마감 detail 에서 모델/할인/세트 마스터 lookup 시 본 record 사용.
 */
public record ProductSummary(
        UUID id,
        String name,
        String modelName,
        UUID categoryId,
        BigDecimal sellingPrice,
        String status) {
}
