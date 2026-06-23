package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.CollectionPlan;
import com.samhanair.logis.accounting.domain.PlanBasis;
import com.samhanair.logis.accounting.domain.PlanStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 수금계획 응답. UUID 는 노출하지 않고 planNo 와 거래처 표시 식별자만 반환한다. */
public record CollectionPlanResponse(
        String planNo,
        String partnerCode,
        String bizNo,
        String partnerName,
        LocalDate plannedDate,
        BigDecimal plannedAmount,
        PlanBasis basis,
        PlanStatus status,
        String memo
) {
    public static CollectionPlanResponse of(CollectionPlan plan, PartnerDisplay partner) {
        return new CollectionPlanResponse(
                plan.getPlanNo(),
                partner.partnerCode(),
                partner.bizNo(),
                partner.partnerName(),
                plan.getPlannedDate(),
                plan.getPlannedAmount(),
                plan.getBasis(),
                plan.getStatus(),
                plan.getMemo()
        );
    }

    /** API 표시용 거래처 정보. */
    public record PartnerDisplay(String partnerCode, String bizNo, String partnerName) {
    }
}
