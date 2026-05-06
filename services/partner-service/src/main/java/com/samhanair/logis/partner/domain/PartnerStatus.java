package com.samhanair.logis.partner.domain;

/**
 * 거래처 상태.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — 정상 거래 가능. 신규 슬립 발행 / 결제 모두 허용.</li>
 *   <li>{@link #SUSPENDED} — 일시 거래 중지 (예: 미수금 한도 초과). 신규 슬립 발행 차단,
 *       결제는 허용.</li>
 *   <li>{@link #TERMINATED} — 거래 종료 (계약 해지). 신규 슬립 발행 / 결제 모두 차단,
 *       조회만 허용. soft-delete 와는 구분 — 거래 종료 후에도 정산 / 회계 조회 목적으로 보관.</li>
 * </ul>
 */
public enum PartnerStatus {

    ACTIVE,
    SUSPENDED,
    TERMINATED
}
