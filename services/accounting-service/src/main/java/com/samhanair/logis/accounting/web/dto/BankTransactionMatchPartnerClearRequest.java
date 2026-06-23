package com.samhanair.logis.accounting.web.dto;

/** 통장 거래 거래처 수동지정 해제 요청. */
public record BankTransactionMatchPartnerClearRequest(
        String bankAccountLabel,
        String externalRef
) {
}
