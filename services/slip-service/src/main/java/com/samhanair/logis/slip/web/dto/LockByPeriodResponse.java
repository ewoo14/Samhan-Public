package com.samhanair.logis.slip.web.dto;

import java.time.LocalDate;

/**
 * 기간 마감 lock 응답 — 처리 건수 + 적용 기간 echo.
 */
public record LockByPeriodResponse(
        LocalDate startDate,
        LocalDate endDate,
        String status,
        int lockedCount) {
}
