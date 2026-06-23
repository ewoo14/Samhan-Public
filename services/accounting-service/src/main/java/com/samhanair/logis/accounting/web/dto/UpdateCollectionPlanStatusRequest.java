package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.PlanStatus;
import jakarta.validation.constraints.NotNull;

/** 수금계획 상태 전이 요청. */
public record UpdateCollectionPlanStatusRequest(@NotNull PlanStatus status) {
}
