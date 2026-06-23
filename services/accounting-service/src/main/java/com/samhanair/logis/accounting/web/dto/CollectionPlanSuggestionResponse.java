package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.PlanBasis;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 수금계획 자동 제안 응답. UUID 는 포함하지 않는다. */
public record CollectionPlanSuggestionResponse(
        String partnerCode,
        String bizNo,
        String partnerName,
        LocalDate plannedDate,
        BigDecimal plannedAmount,
        PlanBasis basis,
        String sourceReference,
        String memo
) {
}
