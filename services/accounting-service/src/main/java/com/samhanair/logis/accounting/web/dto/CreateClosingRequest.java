package com.samhanair.logis.accounting.web.dto;

import com.samhanair.logis.accounting.domain.PeriodType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 매출 마감 실행 요청 — POST /accounting/closings.
 *
 * <p>periodType DAILY 면 periodDate = 해당 일자, MONTHLY 면 해당 월의 임의 일자
 * (service 가 1일로 normalize).
 */
public record CreateClosingRequest(
        @NotNull(message = "periodType 은 필수입니다")
        PeriodType periodType,

        @NotNull(message = "periodDate 는 필수입니다")
        LocalDate periodDate,

        @Size(max = 500, message = "description 은 최대 500자입니다")
        String description
) {}
