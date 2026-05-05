package com.samhanair.logis.product.domain;

/**
 * 품목 유형 — 단일 품목(SINGLE) vs 세트 품목(BUNDLE).
 *
 * <p>출처: DOMAIN-EXTENSIONS §2 옵션 A. BUNDLE 인 경우 {@link BundleMode} 와
 * {@code BundleComponent} 1:N 으로 component 라인을 보유한다.
 */
public enum ProductType {
    /** 일반 단일 SKU. */
    SINGLE,
    /** 세트 SKU (자식 component 보유). */
    BUNDLE
}
