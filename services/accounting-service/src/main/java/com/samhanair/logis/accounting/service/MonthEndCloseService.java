package com.samhanair.logis.accounting.service;

import com.samhanair.logis.accounting.client.SlipServiceClient;
import com.samhanair.logis.accounting.domain.AccountingPeriod;
import com.samhanair.logis.accounting.domain.PeriodStatus;
import com.samhanair.logis.accounting.domain.PeriodType;
import com.samhanair.logis.accounting.repository.AccountingPeriodRepository;
import com.samhanair.logis.accounting.repository.JournalLineRepository;
import com.samhanair.logis.accounting.repository.JournalLineRepository.AccountTotal;
import com.samhanair.logis.accounting.web.dto.AccountingPeriodResponse;
import com.samhanair.logis.accounting.web.dto.CreateClosingRequest;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매출 마감 service (Phase 10 Step 8 — P2-4).
 *
 * <p>매뉴얼 출처: {@code docs/manual/02-창고/04-매출-마감.md} §3-1.
 *
 * <p>라이프사이클 표 (Layer 4 의무):
 *
 * <pre>
 *   close (DAILY|MONTHLY) : OPEN → CLOSED
 *                            (1) period_date normalize (월별 = 1일)
 *                            (2) slip-service.lock-by-period 호출 → CONFIRMED 슬립 LOCKED
 *                            (3) POSTED 분개 합계 집계 (REVENUE/COST_OF_SALES/SGA) stamp
 *                            (4) AccountingPeriod CLOSED + closed_at/by 기록
 *   reverse               : CLOSED → OPEN (MASTER 만 — controller 가드)
 *   list                  : period_type / year 필터
 * </pre>
 *
 * <p>마감된 기간에 속한 분개 입력은 {@code AccountingPeriodGuard} 가 차단.
 *
 * <p>본 service 는 {@link SlipServiceClient} 외부 client 의존 — IT 에서 {@code @MockBean} 격리 의무
 * (메모리 가드 {@code feedback_it_mockbean_external_clients.md}).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MonthEndCloseService {

    private final AccountingPeriodRepository periodRepository;
    private final JournalLineRepository journalLineRepository;
    private final SlipServiceClient slipServiceClient;

    /**
     * 마감 실행 — 일별 또는 월별. 동일 (type, period_date) row 가 OPEN 이면 재사용
     * (역마감 후 재마감 use-case), CLOSED 면 CONFLICT.
     */
    public AccountingPeriodResponse close(CreateClosingRequest request, String actorUserId) {
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new IllegalArgumentException("actorUserId 는 필수입니다");
        }
        LocalDate normalized = normalize(request.periodType(), request.periodDate());
        AccountingPeriod period = periodRepository
                .findByPeriodTypeAndPeriodDate(request.periodType(), normalized)
                .orElseGet(() -> periodRepository.save(
                        AccountingPeriod.create(request.periodType(), normalized,
                                request.description())));

        if (period.getStatus() == PeriodStatus.CLOSED) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 마감된 기간입니다: " + period.getPeriodType() + " " + period.getPeriodDate());
        }

        // (1) slip-service 잠금 호출.
        LocalDate from = periodFrom(request.periodType(), normalized);
        LocalDate to = periodTo(request.periodType(), normalized);
        int lockedCount = slipServiceClient.lockByPeriod(from, to);

        // (2) 회계 합계 집계 (POSTED 분개만).
        List<AccountTotal> totals = journalLineRepository.aggregatePostedByAccount(from, to);
        BigDecimal totalSales = sumByPrefix(totals, "4");
        BigDecimal totalPurchase = sumByPrefix(totals, "5");
        BigDecimal totalExpense = sumByPrefix(totals, "8");

        // (3) close 트랜지션.
        period.close(actorUserId, totalSales, totalPurchase, totalExpense, lockedCount);
        return AccountingPeriodResponse.of(period);
    }

    /**
     * 역마감 — CLOSED → OPEN. controller 가 MASTER 권한 가드 (PreAuthorize).
     */
    public AccountingPeriodResponse reverse(UUID id, String actorUserId) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "존재하지 않는 마감입니다: " + id));
        period.reverse(actorUserId);
        return AccountingPeriodResponse.of(period);
    }

    /** 필터 조회 — period_type / year (year null 이면 전체). */
    @Transactional(readOnly = true)
    public List<AccountingPeriodResponse> list(PeriodType periodType, Integer year) {
        LocalDate from = year == null ? null : LocalDate.of(year, 1, 1);
        LocalDate to = year == null ? null : LocalDate.of(year, 12, 31);
        return periodRepository.findByFilters(periodType, from, to).stream()
                .map(AccountingPeriodResponse::of)
                .toList();
    }

    /**
     * Guard 헬퍼 — 주어진 일자가 마감된 기간에 속하는지 (DAILY 동일 일자 또는 MONTHLY 동일 월) 검사.
     * 1건이라도 발견되면 첫 row 반환. AccountingPeriodGuard interceptor 사용.
     */
    @Transactional(readOnly = true)
    public Optional<AccountingPeriod> findClosedPeriodCovering(LocalDate journalDate) {
        if (journalDate == null) {
            return Optional.empty();
        }
        LocalDate monthFirst = journalDate.withDayOfMonth(1);
        return periodRepository.findCoveringClosedPeriod(PeriodStatus.CLOSED, journalDate, monthFirst)
                .stream().findFirst();
    }

    private static LocalDate normalize(PeriodType type, LocalDate date) {
        return switch (type) {
            case DAILY -> date;
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate periodFrom(PeriodType type, LocalDate normalized) {
        return switch (type) {
            case DAILY -> normalized;
            case MONTHLY -> normalized.withDayOfMonth(1);
        };
    }

    private static LocalDate periodTo(PeriodType type, LocalDate normalized) {
        return switch (type) {
            case DAILY -> normalized;
            case MONTHLY -> normalized.withDayOfMonth(normalized.lengthOfMonth());
        };
    }

    /**
     * 매출/매입/판관비 합계 산출 — accountCode prefix 가 일치하는 row 의 (credit-debit) 또는
     * (debit-credit) 누적. REVENUE(4): credit-debit (대변잔액). COST_OF_SALES(5)/SGA(8):
     * debit-credit (차변잔액).
     */
    private static BigDecimal sumByPrefix(List<AccountTotal> totals, String prefix) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean creditNormal = "4".equals(prefix);
        for (AccountTotal t : totals) {
            if (t.getAccountCode() != null && t.getAccountCode().startsWith(prefix)) {
                BigDecimal d = nullToZero(t.getDebitTotal());
                BigDecimal c = nullToZero(t.getCreditTotal());
                BigDecimal delta = creditNormal ? c.subtract(d) : d.subtract(c);
                sum = sum.add(delta);
            }
        }
        return sum;
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
