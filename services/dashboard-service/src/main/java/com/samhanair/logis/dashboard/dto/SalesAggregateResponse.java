package com.samhanair.logis.dashboard.dto;

import com.samhanair.logis.dashboard.domain.SalesAggregate;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 매출 집계 응답 DTO — admin 화면 노출.
 *
 * <p>UUID 비공개 가드 — partnerId (UUID) 노출 X. partnerCode 만 노출 (호출자가 매핑 후 부여).
 */
public record SalesAggregateResponse(
        LocalDate aggregateDate,
        String partnerCode,
        BigDecimal amount,
        int itemCount
) {

    public static SalesAggregateResponse from(SalesAggregate aggregate, String partnerCode) {
        return new SalesAggregateResponse(
                aggregate.getAggregateDate(),
                partnerCode,
                aggregate.getAmount(),
                aggregate.getItemCount());
    }
}
