package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.AccountingPeriod;
import com.samhanair.logis.accounting.domain.PeriodStatus;
import com.samhanair.logis.accounting.domain.PeriodType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * AccountingPeriod — 회계 마감 기간. (period_type, period_date) UNIQUE.
 *
 * <p>{@link #findCoveringClosedPeriod} — Guard interceptor 가 분개 입력 시 해당 journalDate 가
 * 마감된 일별 / 월별 기간에 속하는지 검사할 때 사용.
 */
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {

    Optional<AccountingPeriod> findByPeriodTypeAndPeriodDate(PeriodType periodType,
                                                             LocalDate periodDate);

    /**
     * 연도(YYYY) + period_type 필터 조회 — controller GET 용.
     * year null 이면 전체 연도, periodType null 이면 전체 유형.
     */
    @Query("""
            SELECT p FROM AccountingPeriod p
            WHERE (:periodType IS NULL OR p.periodType = :periodType)
              AND (:from IS NULL OR p.periodDate >= :from)
              AND (:to IS NULL OR p.periodDate <= :to)
            ORDER BY p.periodDate DESC, p.periodType ASC
            """)
    List<AccountingPeriod> findByFilters(@Param("periodType") PeriodType periodType,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    /**
     * 주어진 일자가 속한 CLOSED 기간을 조회 — DAILY 동일 일자 또는 MONTHLY 동일 월 1일.
     * Guard interceptor 사용. 1건이라도 발견되면 차단.
     */
    @Query("""
            SELECT p FROM AccountingPeriod p
            WHERE p.status = :status
              AND ((p.periodType = com.samhanair.logis.accounting.domain.PeriodType.DAILY
                        AND p.periodDate = :journalDate)
                OR (p.periodType = com.samhanair.logis.accounting.domain.PeriodType.MONTHLY
                        AND p.periodDate = :monthFirst))
            """)
    List<AccountingPeriod> findCoveringClosedPeriod(@Param("status") PeriodStatus status,
                                                    @Param("journalDate") LocalDate journalDate,
                                                    @Param("monthFirst") LocalDate monthFirst);
}
