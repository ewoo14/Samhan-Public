package com.samhanair.logis.product.domain;

/**
 * BUNDLE 처리 모드 — 견적/주문 라인 분기.
 *
 * <p>출처: DOMAIN-EXTENSIONS §2 옵션 A 보강 + partner-order Code.js SEND_AS_SET_IDS 화이트리스트.
 *
 * <ul>
 *     <li>{@link #EXPAND} (default) — 견적/주문 시 BUNDLE 선택하면 자동으로 component 라인 펼침.</li>
 *     <li>{@link #KEEP} — BUNDLE SKU 자체로 유지 (펼치지 않음). SEND_AS_SET_IDS 4 SKU 만.</li>
 * </ul>
 */
public enum BundleMode {
    /** 견적/주문 시 component 자동 펼침. */
    EXPAND,
    /** BUNDLE 자체 유지 (SEND_AS_SET_IDS). */
    KEEP
}
