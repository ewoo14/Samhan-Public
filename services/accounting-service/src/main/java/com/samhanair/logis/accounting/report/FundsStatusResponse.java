package com.samhanair.logis.accounting.report;

import com.samhanair.logis.accounting.domain.AccountCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자금현황 보고서 응답.
 *
 * <p>자금일보와 자금현황표는 기간만 다른 동일 골격이므로 하나의 DTO 를 공유한다.
 * 계정별/거래처별 금액은 사용자 표시명(accountName, bizNo, partnerName)만 포함하며 partner UUID 는
 * 응답에 포함하지 않는다.
 *
 * @param fromDate 조회 시작일
 * @param toDate 조회 종료일
 * @param groups 자금 계정그룹별 섹션
 * @param total 전체 합계
 * @param generatedAt 보고서 생성 시각
 */
public record FundsStatusResponse(
        LocalDate fromDate,
        LocalDate toDate,
        List<AccountGroup> groups,
        AmountSummary total,
        LocalDateTime generatedAt
) {

    /**
     * 자금 계정그룹 섹션.
     *
     * @param groupCode 내부 그룹 코드
     * @param groupName 사용자 표시 그룹명
     * @param accounts 계정별 섹션
     * @param subtotal 그룹 소계
     */
    public record AccountGroup(
            String groupCode,
            String groupName,
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
     * @param lines 거래처별 라인
     * @param subtotal 계정 소계
     */
    public record AccountSection(
            String accountCode,
            String accountName,
            AccountCategory category,
            List<Line> lines,
            AmountSummary subtotal
    ) {
    }

    /**
     * 자금현황 거래처별 라인.
     *
     * <p>UUID 비공개 원칙에 따라 partnerId 는 노출하지 않고 bizNo/partnerName 만 반환한다.
     *
     * @param accountCode 계정코드
     * @param accountName 계정명
     * @param bizNo 사업자번호 숫자 문자열. 기타/미조회 라인은 빈 문자열
     * @param partnerName 거래처명 또는 기타
     * @param openingBalance 이월잔액
     * @param increase 기간 증가
     * @param decrease 기간 감소
     * @param closingBalance 금일잔액
     */
    public record Line(
            String accountCode,
            String accountName,
            String bizNo,
            String partnerName,
            BigDecimal openingBalance,
            BigDecimal increase,
            BigDecimal decrease,
            BigDecimal closingBalance
    ) {
    }

    /**
     * 금액 4종 요약.
     *
     * @param openingBalance 이월잔액 합계
     * @param increase 증가 합계
     * @param decrease 감소 합계
     * @param closingBalance 금일잔액 합계
     */
    public record AmountSummary(
            BigDecimal openingBalance,
            BigDecimal increase,
            BigDecimal decrease,
            BigDecimal closingBalance
    ) {
        /** 0원 요약. */
        public static AmountSummary zero() {
            return new AmountSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        /** 두 요약을 더한다. */
        public AmountSummary plus(AmountSummary other) {
            return new AmountSummary(
                    openingBalance.add(other.openingBalance),
                    increase.add(other.increase),
                    decrease.add(other.decrease),
                    closingBalance.add(other.closingBalance)
            );
        }
    }
}
