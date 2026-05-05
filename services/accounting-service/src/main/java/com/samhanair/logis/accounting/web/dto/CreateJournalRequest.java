package com.samhanair.logis.accounting.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * 분개 신규 생성 요청 — POST /accounting/journals.
 * 생성 직후 status DRAFT. 라인 1개 이상 필수 (post 시 차/대 합계 일치 검증).
 */
public record CreateJournalRequest(
        @NotNull(message = "journalDate 는 필수입니다")
        LocalDate journalDate,

        @Size(max = 500, message = "description 은 최대 500자입니다")
        String description,

        @NotNull(message = "lines 는 1개 이상 필수입니다")
        @NotEmpty(message = "lines 는 1개 이상 필수입니다")
        @Valid
        List<CreateJournalLineRequest> lines
) {}
