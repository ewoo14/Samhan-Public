package com.samhanair.logis.product.domain;

/**
 * 시트 출처별 내부 카테고리 — ProductSpec/시드 변환용 (사용자 노출 X).
 *
 * <p>출처: Migration Plan §2.1.1. {@link EstimateCategory} 와 별도 (운영 노출 vs 시드 출처 구분).
 */
public enum ProductCategory {
    HOME_MULTI,
    SINGLE_SET,
    SINGLE_PART,
    COMMERCIAL_MULTI,
    COMMERCIAL_PART,
    OLD,
    MATERIAL
}
