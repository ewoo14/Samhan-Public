package com.samhanair.logis.accounting.web.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 통장 거래 거래처 수동지정 해제 요청.
 *
 * <p>식별 자연키 = {@code bankAccountLabel + transactedAt + amount + externalRef} (V43 unique index 4-key).
 */
public record BankTransactionMatchPartnerClearRequest(
        String bankAccountLabel,
        LocalDateTime transactedAt,
        BigDecimal amount,
        String externalRef
) {
}
