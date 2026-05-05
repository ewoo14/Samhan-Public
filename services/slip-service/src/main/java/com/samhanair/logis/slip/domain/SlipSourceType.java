package com.samhanair.logis.slip.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 전표 발행 출처 — Phase 6 M5 (slip-service-integration) 신규.
 *
 * <p>설계 문서: {@code docs/migration/phase6/M5-slip-service-integration.md} §3 payload 매핑.
 *
 * <ul>
 *   <li>{@link #ESTIMATE} — estimate-app v2 의 견적 finalize → 자동 출고전표 발행
 *       (legacy {@code sendOrderFromUi} 의 e-Count {@code SaleList POST} 대체).</li>
 *   <li>{@link #PARTNER_ORDER} — partner-order-service M4 의 협력사 주문 승인 → 출고전표 발행.</li>
 *   <li>{@link #MANUAL} — 사용자가 데스크톱/모바일에서 직접 작성한 전표
 *       (기본값. 기존 {@code POST /slips} 경로는 이 값으로 저장).</li>
 *   <li>{@link #MIGRATED_ECOUNT} — legacy e-Count 에서 batch 마이그레이션된 전표 (별도 batch).</li>
 * </ul>
 *
 * <p>Idempotency 키와 함께 {@code (sourceType, sourceId)} 복합 인덱스로 동일 estimate/order
 * 의 중복 발행을 추가 보호한다 (DB partial UNIQUE INDEX 는 idempotencyKey 단독).
 */
@Getter
@RequiredArgsConstructor
public enum SlipSourceType {
    ESTIMATE("견적 자동 발행"),
    PARTNER_ORDER("협력사 주문 발행"),
    MANUAL("수기 작성"),
    MIGRATED_ECOUNT("legacy 마이그레이션");

    private final String displayName;
}
