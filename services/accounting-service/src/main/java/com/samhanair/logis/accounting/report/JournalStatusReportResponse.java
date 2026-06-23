package com.samhanair.logis.accounting.report;

import com.samhanair.logis.accounting.domain.JournalSourceType;
import com.samhanair.logis.accounting.domain.JournalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 전표현황 보고서 응답. */
public record JournalStatusReportResponse(
        LocalDate fromDate,
        LocalDate toDate,
        JournalStatus status,
        List<JournalSourceType> sourceTypes,
        JournalStatusGroupBy groupBy,
        List<Group> groups,
        Summary total,
        LocalDateTime generatedAt) {

    /** grouping 단위. */
    public record Group(
            String groupKey,
            String groupLabel,
            List<Line> lines,
            Summary subtotal) {
    }

    /**
     * 전표현황 행.
     *
     * <p>UUID 비공개 원칙에 따라 전표 ID/거래처 ID 는 포함하지 않는다.
     */
    public record Line(
            String journalNo,
            LocalDate journalDate,
            JournalSourceType sourceType,
            String sourceTypeDisplayName,
            String partnerName,
            String description,
            BigDecimal totalDebit,
            BigDecimal totalCredit) {
    }

    /** 차/대 합계와 전표 건수 요약. */
    public record Summary(
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            int journalCount) {

        /** 0 요약. */
        public static Summary zero() {
            return new Summary(BigDecimal.ZERO, BigDecimal.ZERO, 0);
        }

        /** 행 1건을 더한다. */
        public Summary plus(Line line) {
            return new Summary(
                    totalDebit.add(line.totalDebit()),
                    totalCredit.add(line.totalCredit()),
                    journalCount + 1
            );
        }

        /** 다른 요약을 더한다. */
        public Summary plus(Summary other) {
            return new Summary(
                    totalDebit.add(other.totalDebit()),
                    totalCredit.add(other.totalCredit()),
                    journalCount + other.journalCount()
            );
        }
    }
}
