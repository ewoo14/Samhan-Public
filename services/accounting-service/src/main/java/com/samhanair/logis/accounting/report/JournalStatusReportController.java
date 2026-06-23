package com.samhanair.logis.accounting.report;

import com.samhanair.logis.accounting.domain.JournalSourceType;
import com.samhanair.logis.accounting.domain.JournalStatus;
import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전표현황 REST endpoint.
 *
 * <p>eCount 전표현황 요구를 기존 분개장 검색과 분리한 read-only 보고서로 제공한다.
 * 출처 다중 필터, 거래처 필터, 일자/출처/거래처 grouping 을 지원한다.
 *
 * <p>권한 page-code 는 재무 보고서 공통 {@code accounting.reports} VIEW 를 사용한다.
 */
@Tag(name = "전표현황", description = "전표현황 / 회계거래현황")
@RestController
@RequestMapping("/accounting/reports")
@RequiredArgsConstructor
public class JournalStatusReportController {

    private static final String ROLE_HEADER = "X-User-Role";

    private final JournalStatusReportService journalStatusReportService;
    private final ReportPermissionGuard reportPermissionGuard;

    /**
     * 전표현황 조회.
     *
     * @param from 조회 시작일
     * @param to 조회 종료일
     * @param sourceTypes 출처 다중 필터. 미지정 시 전체
     * @param partnerId 거래처 UUID 필터. 응답에는 UUID 를 노출하지 않는다
     * @param groupBy grouping 기준. 기본 DATE
     * @param status 상태 필터. 기본 POSTED
     * @param roleHeader X-User-Role 헤더
     * @return 전표현황 그룹 응답
     */
    @Operation(
            summary = "전표현황 조회",
            description = "POSTED 분개 기준 전표번호/일자/거래유형/거래처/적요/차대변 합계를 조회합니다. " +
                    "sourceTypes 다중 필터, partnerId 필터, DATE/SOURCE_TYPE/PARTNER grouping 을 지원합니다.")
    @GetMapping("/journal-status")
    @RequirePermission(page = ReportPermissionGuard.PAGE_CODE, action = PermissionAction.VIEW)
    public ApiResponse<JournalStatusReportResponse> journalStatus(
            @Parameter(description = "조회 시작일 (YYYY-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일 (YYYY-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "출처 다중 필터. 예: sourceTypes=SLIP&sourceTypes=MANUAL")
            @RequestParam(required = false) Set<JournalSourceType> sourceTypes,
            @Parameter(description = "거래처 UUID 필터. 응답에는 노출하지 않음")
            @RequestParam(required = false) UUID partnerId,
            @Parameter(description = "그룹 기준: DATE / SOURCE_TYPE / PARTNER")
            @RequestParam(defaultValue = "DATE") JournalStatusGroupBy groupBy,
            @Parameter(description = "전표 상태. 기본 POSTED")
            @RequestParam(defaultValue = "POSTED") JournalStatus status,
            @RequestHeader(value = ROLE_HEADER, required = false) String roleHeader) {
        reportPermissionGuard.checkView(roleHeader);
        return ApiResponse.ok(journalStatusReportService.findStatus(
                from, to, sourceTypes, partnerId, groupBy, status));
    }
}
