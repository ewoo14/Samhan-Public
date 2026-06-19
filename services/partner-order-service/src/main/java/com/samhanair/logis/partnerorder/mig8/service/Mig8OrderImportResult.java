package com.samhanair.logis.partnerorder.mig8.service;

/** MIG-8 주문 이식 결과 카운터. */
public record Mig8OrderImportResult(
        int fetchedCount,
        int createdCount,
        int skippedCount,
        int rejectedCount) {

    public Mig8OrderImportResult plus(Mig8OrderImportResult other) {
        return new Mig8OrderImportResult(
                fetchedCount + other.fetchedCount,
                createdCount + other.createdCount,
                skippedCount + other.skippedCount,
                rejectedCount + other.rejectedCount);
    }

    public static Mig8OrderImportResult fetched() {
        return new Mig8OrderImportResult(1, 0, 0, 0);
    }

    public static Mig8OrderImportResult created() {
        return new Mig8OrderImportResult(0, 1, 0, 0);
    }

    public static Mig8OrderImportResult skipped() {
        return new Mig8OrderImportResult(0, 0, 1, 0);
    }

    public static Mig8OrderImportResult rejected() {
        return new Mig8OrderImportResult(0, 0, 0, 1);
    }

    public static Mig8OrderImportResult empty() {
        return new Mig8OrderImportResult(0, 0, 0, 0);
    }
}
