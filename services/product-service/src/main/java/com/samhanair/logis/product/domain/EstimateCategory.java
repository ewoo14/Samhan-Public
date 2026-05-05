package com.samhanair.logis.product.domain;

/**
 * 견적서 카테고리 분류 — {@link UsageScope#ESTIMATE} 또는 {@link UsageScope#BOTH} 인 경우만 채움.
 *
 * <p>출처: DOMAIN-EXTENSIONS §3. SpecKeyTemplate 의 키.
 *
 * <ul>
 *     <li>{@link #HOME_MULTI} — 홈멀티</li>
 *     <li>{@link #SINGLE_SET} — 싱글 세트 (BUNDLE 부모)</li>
 *     <li>{@link #COMMERCIAL_MULTI} — 상업멀티</li>
 *     <li>{@link #LEGACY} — 구형 (50% DC)</li>
 *     <li>{@link #OTHER} — 기타 (사용자 자유 입력)</li>
 * </ul>
 */
public enum EstimateCategory {
    HOME_MULTI,
    SINGLE_SET,
    COMMERCIAL_MULTI,
    LEGACY,
    OTHER
}
