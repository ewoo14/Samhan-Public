package com.samhanair.logis.product.domain;

/**
 * 자재 옵션 키 — 싱글 자재가격 시트의 D 열 master cell 인덱스.
 *
 * <p>출처: DOMAIN-EXTENSIONS §1 (G8 확정) — formulas.json grep 결과.
 *
 * <ul>
 *     <li>{@link #D4} — 자재 합계 default master (245 hits, 가장 많음)</li>
 *     <li>{@link #D7} — 자재 미포함 (45 hits)</li>
 *     <li>{@link #D8} — 자재 포함 (10 hits)</li>
 * </ul>
 */
public enum MaterialKey {
    D4,
    D7,
    D8
}
