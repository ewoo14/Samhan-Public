package com.samhanair.logis.accounting.web;

import com.samhanair.logis.accounting.service.HometaxExportService;
import com.samhanair.logis.accounting.service.LedgerImageService;
import com.samhanair.logis.accounting.service.MonthEndCloseService;
import com.samhanair.logis.accounting.service.SalesAggregateService;
import com.samhanair.logis.accounting.service.StatementBatchService;
import com.samhanair.logis.accounting.web.dto.DailyClosingDetailResponse;
import com.samhanair.logis.accounting.web.dto.LedgerImageResponse;
import com.samhanair.logis.accounting.web.dto.SalesAggregateRow;
import com.samhanair.logis.accounting.web.dto.StatementBatchRow;
import com.samhanair.logis.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회계 리포트 통합 endpoint (PR-E2 BE-A8/A9/A10/A11/A12).
 *
 * <p>legacy GAS B 카테고리 4건 (원장/거래명세서/계산서/일마감) + 매출집계 1건 — Samhan Public 자체
 * 분개/세금계산서 자동 조회로 이식. 이카운트 의존 0.
 *
 * <p>endpoint 매트릭스 (모두 ACCOUNTANT/MASTER 가드):
 * <ul>
 *   <li>GET  /accounting/sales/aggregate              — BE-A8 매출/수금/채권 집계</li>
 *   <li>GET  /accounting/journals/ledger-data         — BE-A9 거래처별 원장</li>
 *   <li>GET  /accounting/statements/batch-data        — BE-A10 거래명세서 batch</li>
 *   <li>GET  /accounting/tax-invoice/hometax-export   — BE-A11 홈택스 일괄 (xlsx)</li>
 *   <li>GET  /accounting/closings/daily               — BE-A12 일별 마감 detail</li>
 * </ul>
 *
 * <p>UUID 비공개 가드 — 응답은 partnerCode + partnerName + slipNo / taxInvoiceNo / journalNo
 * 만 노출. 모든 응답 ApiResponse 래핑 (xlsx 제외 — binary).
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class AccountingReportController {

    private final SalesAggregateService salesAggregateService;
    private final LedgerImageService ledgerImageService;
    private final StatementBatchService statementBatchService;
    private final HometaxExportService hometaxExportService;
    private final MonthEndCloseService monthEndCloseService;

    /** BE-A8 매출/수금/채권 집계. */
    @Operation(summary = "매출/수금/채권 집계 (BE-A8)",
            description = "기간 + 거래처 단일/전체 필터 — 자체 분개 401/110 코드 기반 합계")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "from/to 누락 또는 역순")
    })
    @GetMapping("/accounting/sales/aggregate")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<List<SalesAggregateRow>> aggregate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String partnerCode) {
        return ApiResponse.ok(salesAggregateService.aggregate(from, to, partnerCode));
    }

    /** BE-A9 거래처별 원장 데이터. */
    @Operation(summary = "거래처별 원장 (BE-A9)",
            description = "partner snapshot + 단톡방 매핑 + 분개 line 시간순 + 누적 잔액")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "partnerCode 미존재")
    })
    @GetMapping("/accounting/journals/ledger-data")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<LedgerImageResponse> ledger(
            @RequestParam String partnerCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(ledgerImageService.getLedger(partnerCode, from, to));
    }

    /** BE-A10 거래명세서 batch. */
    @Operation(summary = "거래명세서 batch (BE-A10)",
            description = "기간 ISSUED 세금계산서 → 거래처별 그룹핑 + 라인 snapshot + 단톡방")
    @GetMapping("/accounting/statements/batch-data")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<List<StatementBatchRow>> statementBatch(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(statementBatchService.batch(from, to));
    }

    /** BE-A11 홈택스 일괄 업로드 양식 export (binary xlsx). */
    @Operation(summary = "홈택스 일괄 양식 (BE-A11)",
            description = "기간 ISSUED 세금계산서 → 홈택스 표준 컬럼 xlsx (100건 분할 sheet)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "xlsx binary"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "workbook 직렬화 실패")
    })
    @GetMapping("/accounting/tax-invoice/hometax-export")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ResponseEntity<byte[]> hometaxExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] xlsx = hometaxExportService.export(from, to);
        String filename = "hometax-export_" + from.format(DateTimeFormatter.BASIC_ISO_DATE)
                + "_" + to.format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(xlsx);
    }

    /** BE-A12 일별 세금계산서 마감 detail. */
    @Operation(summary = "일별 마감 detail (BE-A12)",
            description = "일별 매출/세금계산서/할인 detail — read-only (마감 OPEN/CLOSED 무관)")
    @GetMapping("/accounting/closings/daily")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<DailyClosingDetailResponse> dailyDetail(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(monthEndCloseService.getDailyDetail(date));
    }
}
