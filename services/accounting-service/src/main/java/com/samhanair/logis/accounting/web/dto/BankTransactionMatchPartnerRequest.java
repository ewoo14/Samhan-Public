package com.samhanair.logis.accounting.web.dto;

/** 통장 거래 거래처 수동지정 요청. */
public record BankTransactionMatchPartnerRequest(
        String bankAccountLabel,
        String externalRef,
        String partnerCode
) {
}
