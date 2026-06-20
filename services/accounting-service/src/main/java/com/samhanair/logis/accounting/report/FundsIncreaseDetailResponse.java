package com.samhanair.logis.accounting.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자금 증가 drill-down 응답.
 *
 * <p>자금의증가와 자금증감내역은 동일한 drill-down 이므로 하나의 DTO 를 공유한다.
 * 상대 거래처도 사용자 표시명만 포함하고 UUID 는 노출하지 않는다.
 *
 * @param fromDate 조회 시작일
 * @param toDate 조회 종료일
 * @param accountCode 대상 자금 계정코드
 * @param accountName 대상 자금 계정명
 * @param partnerName 대상 거래처명. partnerId 미지정 조회이면 null
 * @param lines 증가 상세 라인
 * @param totalAmount 상세 금액 합계
 * @param generatedAt 보고서 생성 시각
 */
public record FundsIncreaseDetailResponse(
        LocalDate fromDate,
        LocalDate toDate,
        String accountCode,
        String accountName,
        String partnerName,
        List<Line> lines,
        BigDecimal totalAmount,
        LocalDateTime generatedAt
) {

    /**
     * 증가 상세 라인.
     *
     * @param txDate 거래일자
     * @param counterAccountName 상대계정명
     * @param counterPartnerName 상대거래처명
     * @param description 적요
     * @param amount 증가 금액
     */
    public record Line(
            LocalDate txDate,
            String counterAccountName,
            String counterPartnerName,
            String description,
            BigDecimal amount
    ) {
    }
}
