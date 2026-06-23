package com.samhanair.logis.accounting.report;

import com.samhanair.logis.accounting.domain.AccountCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 계정명세서 응답.
 *
 * <p>특정 기준일의 계정×거래처 잔액 스냅샷이다. 기본 조회는 채권/채무 계정을 대상으로
 * 하며, {@code accountCode} 지정 시 단일 계정만 조회한다. 거래처 UUID 는 응답에 포함하지
 * 않고, 거래처명만 표시한다.
 *
 * @param asOfDate 기준일
 * @param accountCode 요청 계정코드. 전체 조회이면 null
 * @param groups 채권/채무 또는 계정 성격별 그룹
 * @param total 방향별 전체 합계
 * @param generatedAt 생성 시각
 */
public record AccountStatementResponse(
        LocalDate asOfDate,
        String accountCode,
        List<AccountGroup> groups,
        StatementTotal total,
        LocalDateTime generatedAt
) {

    /**
     * 계정명세서 방향별 전체 합계.
     *
     * <p>채권(차변 정상 잔액)과 채무(대변 정상 잔액)는 서로 부호 방향이 달라 단일 잔액으로
     * 더하지 않는다.
     *
     * @param receivableTotal 채권 합계. 조회 결과에 채권 그룹이 없으면 null
     * @param payableTotal 채무 합계. 조회 결과에 채무 그룹이 없으면 null
     */
    public record StatementTotal(
            AmountSummary receivableTotal,
            AmountSummary payableTotal
    ) {
    }

    /**
     * 계정 그룹 섹션.
     *
     * @param groupCode 그룹 코드
     * @param groupName 그룹명
     * @param balanceDirection 정상 잔액 방향
     * @param accounts 계정별 섹션
     * @param subtotal 그룹 소계
     */
    public record AccountGroup(
            String groupCode,
            String groupName,
            BalanceDirection balanceDirection,
            List<AccountSection> accounts,
            AmountSummary subtotal
    ) {
    }

    /**
     * 계정별 섹션.
     *
     * @param accountCode 계정코드
     * @param accountName 계정명
     * @param category 계정 카테고리
     * @param categoryDisplayName 카테고리 표시명
     * @param balanceDirection 정상 잔액 방향
     * @param balanceDirectionDisplayName 정상 잔액 방향 표시명
     * @param lines 거래처별 잔액 라인
     * @param subtotal 계정 소계
     */
    public record AccountSection(
            String accountCode,
            String accountName,
            AccountCategory category,
            String categoryDisplayName,
            BalanceDirection balanceDirection,
            String balanceDirectionDisplayName,
            List<Line> lines,
            AmountSummary subtotal
    ) {
    }

    /**
     * 거래처별 계정명세 라인.
     *
     * @param accountCode 계정코드
     * @param accountName 계정명
     * @param partnerName 거래처명 또는 기타
     * @param openingBalance 특정 기간 시작값이 없으므로 현재 버전은 0
     * @param increase 기준일까지 정상 방향 누계
     * @param decrease 기준일까지 반대 방향 누계
     * @param debitTotal 기준일까지 차변 누계
     * @param creditTotal 기준일까지 대변 누계
     * @param balance 정상 방향 기준 잔액
     */
    public record Line(
            String accountCode,
            String accountName,
            String partnerName,
            BigDecimal openingBalance,
            BigDecimal increase,
            BigDecimal decrease,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            BigDecimal balance
    ) {
    }

    /**
     * 금액 요약.
     *
     * @param openingBalance 이월잔액 합계
     * @param increase 증가누계 합계
     * @param decrease 감소누계 합계
     * @param debitTotal 차변누계 합계
     * @param creditTotal 대변누계 합계
     * @param balance 잔액 합계
     */
    public record AmountSummary(
            BigDecimal openingBalance,
            BigDecimal increase,
            BigDecimal decrease,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            BigDecimal balance
    ) {
        /** 0원 요약. */
        public static AmountSummary zero() {
            return new AmountSummary(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            );
        }

        /** 두 요약을 더한다. */
        public AmountSummary plus(AmountSummary other) {
            return new AmountSummary(
                    openingBalance.add(other.openingBalance),
                    increase.add(other.increase),
                    decrease.add(other.decrease),
                    debitTotal.add(other.debitTotal),
                    creditTotal.add(other.creditTotal),
                    balance.add(other.balance)
            );
        }
    }
}
