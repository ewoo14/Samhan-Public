package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.AccountCategory;
import java.math.BigDecimal;

/**
 * 시산표 1행 — accountCode + 계정명 + 차/대 합계 + 잔액.
 * 잔액 부호 규약: ASSET/COST_OF_SALES/SGA = debit - credit (차변 잔액 양수).
 *                LIABILITY/EQUITY/REVENUE/NON_OPERATING = credit - debit (대변 잔액 양수).
 *                INCOME_TAX = debit - credit (비용 성격).
 */
public record TrialBalanceRowResponse(
        String accountCode,
        String accountName,
        AccountCategory category,
        String categoryDisplayName,
        BigDecimal debitTotal,
        BigDecimal creditTotal,
        BigDecimal balance
) {}
