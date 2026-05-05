package com.samhanair.logis.product.domain;

/**
 * 품목 노출 범위 — 견적/주문 화면에서 라인으로 직접 선택 가능한지 제어.
 *
 * <p>출처: DOMAIN-EXTENSIONS §3 (사용자 명시 2026-05-05) — 분류되지 않은 품목은
 * 견적서/주문서에 나타나지 않음. default = {@link #NONE}.
 *
 * <ul>
 *     <li>{@link #NONE} — 직접 노출 안 됨 (자재/구성품/lookup, backend 만 사용)</li>
 *     <li>{@link #ESTIMATE} — 견적서 모달에서만 선택 가능</li>
 *     <li>{@link #PARTNER_ORDER} — 주문서 모달에서만 선택 가능</li>
 *     <li>{@link #BOTH} — 양쪽 모두 선택 가능 (홈멀티/싱글세트/상업멀티/구형)</li>
 * </ul>
 */
public enum UsageScope {
    NONE,
    ESTIMATE,
    PARTNER_ORDER,
    BOTH
}
