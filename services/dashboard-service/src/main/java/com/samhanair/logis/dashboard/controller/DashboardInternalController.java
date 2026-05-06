package com.samhanair.logis.dashboard.controller;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.dashboard.domain.KpiCategory;
import com.samhanair.logis.dashboard.dto.KpiSnapshotResponse;
import com.samhanair.logis.dashboard.service.KpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 형제 service 가 dashboard KPI 데이터를 조회하는 internal endpoint.
 *
 * <p>인증 = X-Internal-Token 필수 (InternalTokenFilter ROLE_MASTER 부여).
 *
 * <p>UUID 비공개 가드 — 본 응답은 형제 service 한정 (사용자 화면 직접 노출 X).
 */
@RestController
@RequestMapping("/internal/dashboard")
@RequiredArgsConstructor
public class DashboardInternalController {

    private final KpiService kpiService;

    /**
     * category + 날짜 범위로 KPI 시계열 조회.
     */
    @Operation(summary = "KPI 카테고리별 조회 (Internal)",
            description = "형제 service 가 KPI 시계열을 조회. X-Internal-Token 필수.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "범위 무효"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "내부 토큰 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "내부 토큰 누락")
    })
    @GetMapping("/kpi/{category}")
    @PreAuthorize("hasRole('MASTER')")
    public ApiResponse<List<KpiSnapshotResponse>> kpiByCategory(
            @PathVariable KpiCategory category,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(kpiService.findByCategoryAndDateRange(category, from, to).stream()
                .map(KpiSnapshotResponse::from)
                .toList());
    }
}
