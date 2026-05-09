package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.SlipStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 기간 마감 lock 요청 — accounting-service Feign 호출용 (P1-8 Stage 4).
 *
 * @param startDate 기간 시작일 (포함, 필수)
 * @param endDate 기간 종료일 (포함, 필수)
 * @param status 대상 상태 (선택, default CONFIRMED) — 일반적으로 CONFIRMED 호출
 */
public record LockByPeriodRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        SlipStatus status) {
}
