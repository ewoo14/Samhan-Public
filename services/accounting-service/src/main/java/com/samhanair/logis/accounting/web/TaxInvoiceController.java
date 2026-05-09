package com.samhanair.logis.accounting.web;

import com.samhanair.logis.accounting.domain.TaxInvoiceStatus;
import com.samhanair.logis.accounting.service.TaxInvoiceService;
import com.samhanair.logis.accounting.web.dto.CreateTaxInvoiceRequest;
import com.samhanair.logis.accounting.web.dto.TaxInvoiceDetailResponse;
import com.samhanair.logis.accounting.web.dto.TaxInvoiceResponse;
import com.samhanair.logis.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세금계산서 endpoint (Phase 10 Step 8 — P0-4 #3).
 *
 * <p>매뉴얼 출처: {@code docs/manual/03-회계/03-세금계산서.md}.
 *
 * <p>권한 매트릭스 — ACCOUNTANT, MASTER 만 (매뉴얼 §1 + 메모리 ROLE 풀네임 의무):
 *
 * <ul>
 *   <li>POST   /accounting/tax-invoices             — DRAFT 생성</li>
 *   <li>PUT    /accounting/tax-invoices/{id}        — DRAFT 수정</li>
 *   <li>POST   /accounting/tax-invoices/{id}/issue  — DRAFT → ISSUED + 자동 분개</li>
 *   <li>POST   /accounting/tax-invoices/{id}/cancel — ISSUED → CANCELLED + 자동 역분개</li>
 *   <li>GET    /accounting/tax-invoices             — 페이지 조회 (status/period/partner)</li>
 *   <li>GET    /accounting/tax-invoices/{id}        — 단건 + lines</li>
 * </ul>
 *
 * <p>응답은 ApiResponse 래핑. UUID 는 mutation path 에만 사용 — 사용자 표시는 tax_invoice_no.
 */
@RestController
@RequestMapping("/accounting/tax-invoices")
@RequiredArgsConstructor
public class TaxInvoiceController {

    private static final String CALLER_HEADER = "X-User-Id";

    private final TaxInvoiceService taxInvoiceService;

    /** DRAFT 생성. */
    @Operation(summary = "세금계산서 신규 생성", description = "DRAFT 상태로 생성. 라인 1개 이상 필수")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력 검증 실패")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<TaxInvoiceDetailResponse> create(
            @Valid @RequestBody CreateTaxInvoiceRequest request) {
        return ApiResponse.ok(taxInvoiceService.create(request));
    }

    /** DRAFT 수정 — 헤더 + 라인 일괄 교체. */
    @Operation(summary = "세금계산서 수정", description = "DRAFT 상태에서만 가능. 헤더 + 라인 일괄 교체")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "미존재"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DRAFT 가 아닐 때")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<TaxInvoiceDetailResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateTaxInvoiceRequest request) {
        return ApiResponse.ok(taxInvoiceService.update(id, request));
    }

    /** ISSUED 전이 + tax_invoice_no 발급 + 자동 분개 (110/255/400). */
    @Operation(summary = "세금계산서 발행",
            description = "DRAFT → ISSUED. 발행번호 채번 + 자동 분개 (110 외상매출금 / 255 부가세예수금 / 400 매출)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발행 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "미존재"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "DRAFT 가 아니거나 라인 0건/금액 0")
    })
    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<TaxInvoiceDetailResponse> issue(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(taxInvoiceService.issue(id, callerOrSystem(callerHeader)));
    }

    /** CANCELLED 전이 + 자동 역분개. */
    @Operation(summary = "세금계산서 취소",
            description = "ISSUED → CANCELLED. 자동 역분개 (차/대 swap 신규 Journal POSTED)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "미존재"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "ISSUED 가 아닐 때")
    })
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<TaxInvoiceDetailResponse> cancel(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(taxInvoiceService.cancel(id, callerOrSystem(callerHeader)));
    }

    /** 페이지 조회 — 4 필터 (status, from, to, partnerId). 모두 optional. */
    @Operation(summary = "세금계산서 페이지 조회",
            description = "status / 공급일자 [from, to] / partnerId 필터")
    @GetMapping
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<Page<TaxInvoiceResponse>> list(
            @RequestParam(required = false) TaxInvoiceStatus status,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID partnerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(taxInvoiceService.list(status, from, to, partnerId, pageable));
    }

    /** 단건 조회 (라인 포함). */
    @Operation(summary = "세금계산서 단건 조회", description = "라인 포함 상세")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MASTER')")
    public ApiResponse<TaxInvoiceDetailResponse> getOne(@PathVariable UUID id) {
        return ApiResponse.ok(taxInvoiceService.getOne(id));
    }

    private String callerOrSystem(String header) {
        return (header == null || header.isBlank()) ? "system" : header;
    }
}
