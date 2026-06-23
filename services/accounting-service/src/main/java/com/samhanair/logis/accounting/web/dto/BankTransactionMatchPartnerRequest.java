package com.samhanair.logis.accounting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 통장 거래 거래처 수동지정 요청.
 *
 * <p>식별 자연키 = {@code bankAccountLabel + transactedAt + amount + externalRef} (V43 unique index 4-key).
 * UUID 미노출 — 응답이 노출하는 표시 식별자만 사용한다.
 */
public record BankTransactionMatchPartnerRequest(
        String bankAccountLabel,
        LocalDateTime transactedAt,
        BigDecimal amount,
        String externalRef,
        String partnerCode
) {
}
