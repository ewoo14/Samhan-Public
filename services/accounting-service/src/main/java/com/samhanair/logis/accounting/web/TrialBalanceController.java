package com.samhanair.logis.accounting.web;

import com.samhanair.logis.accounting.service.TrialBalanceService;
import com.samhanair.logis.accounting.web.dto.TrialBalanceResponse;
import com.samhanair.logis.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시산표 endpoint (Plan §4).
 *
 * <p>권한: ACCOUNTANT / MASTER.
 * 잔액 부호 규약은 {@link TrialBalanceService} 참조.
 */
@RestController
@RequestMapping("/accounting/balances")
@RequiredArgsConstructor
public class TrialBalanceController {

    private static final DateTimeFormatter PERIOD_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    private final TrialBalanceService trialBalanceService;

    /**
     * 회계 월 시산표 — period=yyyyMM (예: 202604).
     *
     * @param period 회계 월 문자열 (yyyyMM)
     * @throws IllegalArgumentException period 파싱 실패 (400 매핑)
     */
    @Operation(summary = "시산표", description = "POSTED 분개 라인 집계 (yyyyMM)")
    @GetMapping
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<TrialBalanceResponse> byPeriod(@RequestParam String period) {
        YearMonth ym;
        try {
            ym = YearMonth.parse(period, PERIOD_FMT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("period 는 yyyyMM 형식이어야 합니다 (예: 202604)");
        }
        return ApiResponse.ok(trialBalanceService.findByPeriod(ym));
    }
}
