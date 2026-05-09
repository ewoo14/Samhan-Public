package com.samhanair.logis.accounting.repository;

import com.samhanair.logis.accounting.domain.JournalLine;
import com.samhanair.logis.accounting.domain.JournalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JournalLine — 분개 라인. 시산표 집계용 native projection 제공. */
public interface JournalLineRepository extends JpaRepository<JournalLine, UUID> {

    /**
     * 시산표 집계용 — accountCode 별 debit/credit 합계 (POSTED 분개만 포함).
     * 기간 [from, to] 의 journalDate 를 가진 분개의 라인만 집계.
     *
     * <p>반환 row: [accountCode (String), debitTotal (BigDecimal), creditTotal (BigDecimal)].
     */
    @Query("""
            SELECT l.accountCode AS accountCode,
                   COALESCE(SUM(l.debitAmount), 0) AS debitTotal,
                   COALESCE(SUM(l.creditAmount), 0) AS creditTotal
            FROM JournalLine l
            WHERE l.journal.journalDate >= :from
              AND l.journal.journalDate <= :to
              AND l.journal.status = :status
            GROUP BY l.accountCode
            """)
    List<AccountTotal> aggregateByAccount(@Param("from") LocalDate from,
                                          @Param("to") LocalDate to,
                                          @Param("status") JournalStatus status);

    /** Spring Data JPA projection — accountCode 별 차/대 합계. */
    interface AccountTotal {
        String getAccountCode();
        BigDecimal getDebitTotal();
        BigDecimal getCreditTotal();
    }

    /**
     * 마감 합계 집계용 — 계정 대분류(앞 1자리 = 1/2/3/4/5/8/9) prefix 로 그룹핑.
     * 본 슬라이스에서는 service 가 호출 후 prefix 그룹별로 합산. POSTED 분개만 집계.
     */
    @Query("""
            SELECT l.accountCode AS accountCode,
                   COALESCE(SUM(l.debitAmount), 0) AS debitTotal,
                   COALESCE(SUM(l.creditAmount), 0) AS creditTotal
            FROM JournalLine l
            WHERE l.journal.journalDate >= :from
              AND l.journal.journalDate <= :to
              AND l.journal.status = com.samhanair.logis.accounting.domain.JournalStatus.POSTED
            GROUP BY l.accountCode
            """)
    List<AccountTotal> aggregatePostedByAccount(@Param("from") LocalDate from,
                                                @Param("to") LocalDate to);
}
