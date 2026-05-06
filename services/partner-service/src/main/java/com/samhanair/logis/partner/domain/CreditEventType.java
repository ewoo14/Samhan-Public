package com.samhanair.logis.partner.domain;

/**
 * 신용 거래 이력 이벤트 유형.
 *
 * <ul>
 *   <li>{@link #SLIP_ISSUED} — 슬립 발행 시 미수금 증가 (amount 양수).
 *       reference_no = slip 번호.</li>
 *   <li>{@link #PAYMENT} — 결제 입금 시 미수금 차감 (amount 양수, 차감은 service 에서 처리).
 *       reference_no = 입금 transaction 번호 또는 영수증 번호.</li>
 *   <li>{@link #CREDIT_LIMIT_CHANGE} — 신용한도 변경 (delta_credit_limit 양/음수).
 *       amount = 0 고정 (잔액 무영향).</li>
 * </ul>
 */
public enum CreditEventType {

    SLIP_ISSUED,
    PAYMENT,
    CREDIT_LIMIT_CHANGE
}
