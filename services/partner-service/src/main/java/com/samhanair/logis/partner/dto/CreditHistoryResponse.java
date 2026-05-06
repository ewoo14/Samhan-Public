package com.samhanair.logis.partner.dto;

import com.samhanair.logis.partner.domain.CreditEventType;
import com.samhanair.logis.partner.domain.PartnerCreditHistory;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 신용 거래 이력 응답 DTO. UUID 비공개 가드 (memory feedback_uuid_no_user_visibility) 일관 — partner UUID 미포함.
 *
 * @param eventType 이벤트 유형
 * @param amount 금액 (양수)
 * @param deltaCreditLimit 한도 delta (CREDIT_LIMIT_CHANGE 시만 의미)
 * @param balanceAfter 처리 후 잔액 스냅샷
 * @param creditLimitAfter 처리 후 한도 스냅샷
 * @param referenceNo 외부 reference (slip 번호 / 입금 번호)
 * @param note 메모
 * @param occurredAt 발생 시각
 */
public record CreditHistoryResponse(
        CreditEventType eventType,
        BigDecimal amount,
        BigDecimal deltaCreditLimit,
        BigDecimal balanceAfter,
        BigDecimal creditLimitAfter,
        String referenceNo,
        String note,
        LocalDateTime occurredAt
) {

    public static CreditHistoryResponse from(PartnerCreditHistory h) {
        return new CreditHistoryResponse(
                h.getEventType(),
                h.getAmount(),
                h.getDeltaCreditLimit(),
                h.getBalanceAfter(),
                h.getCreditLimitAfter(),
                h.getReferenceNo(),
                h.getNote(),
                h.getOccurredAt());
    }
}
