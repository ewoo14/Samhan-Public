package com.samhanair.logis.accounting.web.dto;

/** 통장 CSV import 결과. */
public record BankTransactionImportResult(
        int totalRows,
        int importedCount,
        int duplicateSkippedCount
) {
}
