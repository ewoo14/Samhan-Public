package com.samhanair.logis.accounting.web.dto;

/** 통장 CSV 범용 컬럼 매핑. 값은 0-based 인덱스 문자열 또는 헤더명이다. */
public record BankTransactionImportMapping(
        String dateColumn,
        String depositColumn,
        String withdrawalColumn,
        String balanceColumn,
        String descriptionColumn,
        String counterpartyColumn,
        String counterpartyAccountColumn,
        String externalRefColumn,
        boolean headerRow
) {
}
