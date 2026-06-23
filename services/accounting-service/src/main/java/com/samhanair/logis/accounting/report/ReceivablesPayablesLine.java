package com.samhanair.logis.accounting.report;

import java.math.BigDecimal;

/**
 * 채권채무 현황 거래처별 행.
 *
 * <p>UUID 사용자 노출 금지 원칙에 따라 거래처 내부 UUID 는 응답에 포함하지 않는다.
 * 화면 식별자는 거래처코드(bizNo), 관리코드(partnerCode), 거래처명만 사용한다.
 *
 * @param bizNo                       거래처코드(사업자번호 숫자)
 * @param partnerCode                 관리코드
 * @param partnerName                 거래처명
 * @param receivableBalance           채권 잔액
 * @param payableBalance              채무 잔액
 * @param netBalance                  순잔액(채권-채무)
 * @param agingBuckets                선택 방향 기준 aging 버킷. ALL 은 채권 양수/채무 음수 net
 * @param creditLimit                 여신한도. partner-service 가 제공하지 않으면 null
 * @param creditUsageRate             여신소진율(%). 한도 없으면 null
 * @param notesHeldAmount             BOARDING/COLLECTING 받을어음 보유액
 * @param notesMaturingSoonAmount     기준일~30일 이내 만기 받을어음
 * @param collectionPlanPlannedAmount PLANNED 수금계획 금액
 * @param collectionPlanOverdueAmount OVERDUE 수금계획 금액
 * @param collectionPlanTotalAmount   PLANNED+OVERDUE 수금계획 금액
 */
public record ReceivablesPayablesLine(
        String bizNo,
        String partnerCode,
        String partnerName,
        BigDecimal receivableBalance,
        BigDecimal payableBalance,
        BigDecimal netBalance,
        ReceivablesPayablesAgingBuckets agingBuckets,
        BigDecimal creditLimit,
        BigDecimal creditUsageRate,
        BigDecimal notesHeldAmount,
        BigDecimal notesMaturingSoonAmount,
        BigDecimal collectionPlanPlannedAmount,
        BigDecimal collectionPlanOverdueAmount,
        BigDecimal collectionPlanTotalAmount
) {
}
