package com.samhanair.logis.accounting.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 분개 라인 1건 생성 요청 — POST /accounting/journals 의 lines[].
 * 차변/대변 한쪽만 양수, 다른쪽 0. 둘 다 0 / 둘 다 양수 거부 (도메인 가드).
 */
public record CreateJournalLineRequest(
        @NotBlank(message = "accountCode 는 필수입니다")
        @Size(max = 6, message = "accountCode 는 최대 6자입니다")
        String accountCode,

        @NotNull(message = "debitAmount 는 필수입니다 (0 이상)")
        @DecimalMin(value = "0", message = "debitAmount 는 0 이상이어야 합니다")
        BigDecimal debitAmount,

        @NotNull(message = "creditAmount 는 필수입니다 (0 이상)")
        @DecimalMin(value = "0", message = "creditAmount 는 0 이상이어야 합니다")
        BigDecimal creditAmount,

        UUID partnerId,

        @Size(max = 500, message = "memo 는 최대 500자입니다")
        String memo
) {}
