package com.samhanair.logis.slip.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.slip.domain.SlipStatus;
import com.samhanair.logis.slip.domain.SlipType;
import com.samhanair.logis.slip.service.SlipService;
import com.samhanair.logis.slip.web.dto.AddLineRequest;
import com.samhanair.logis.slip.web.dto.CreateSlipRequest;
import com.samhanair.logis.slip.web.dto.EditHeaderRequest;
import com.samhanair.logis.slip.web.dto.RejectRequest;
import com.samhanair.logis.slip.web.dto.SlipDetailResponse;
import com.samhanair.logis.slip.web.dto.SlipResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전표 CRUD + 상태 전이 + Inventory 연계 endpoint.
 *
 * <p>권한 매트릭스 (Plan §4):
 * <ul>
 *   <li>조회 — 모든 인증 사용자</li>
 *   <li>작성/수정/저장/전송/취소 — SALES, MANAGER, MASTER</li>
 *   <li>수락/처리/완료/배송/배송완료 — WAREHOUSE, INVENTORY, MANAGER, MASTER</li>
 *   <li>확정 — ACCOUNTANT, MANAGER, MASTER</li>
 *   <li>반려 — MANAGER, MASTER</li>
 * </ul>
 */
@RestController
@RequestMapping("/slips")
@RequiredArgsConstructor
public class SlipController {

    private static final String CALLER_HEADER = "X-User-Id";

    private final SlipService slipService;

    /**
     * 전표 페이지 조회 — slipType / status 옵션 필터.
     *
     * @return 200, Page&lt;SlipResponse&gt;
     */
    @Operation(summary = "전표 페이지 조회", description = "slipType + status 조합 필터 페이지")
    @GetMapping
    public ApiResponse<Page<SlipResponse>> list(
            @RequestParam(required = false) SlipType slipType,
            @RequestParam(required = false) SlipStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(slipService.list(slipType, status, pageable));
    }

    /**
     * 전표 단건 상세 조회.
     *
     * @return 200, SlipDetailResponse / 404 NOT_FOUND
     */
    @Operation(summary = "전표 단건 조회", description = "라인 포함 상세")
    @GetMapping("/{id}")
    public ApiResponse<SlipDetailResponse> getOne(@PathVariable UUID id) {
        return ApiResponse.ok(slipService.getOne(id));
    }

    /**
     * 전표 신규 생성 (DRAFT 상태). 라인 productId 일괄 검증 + 자동 메모 적용.
     *
     * @return 201, SlipDetailResponse
     */
    @Operation(summary = "전표 생성", description = "DRAFT 상태로 생성. 라인 productId 일괄 검증")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "라인/입력 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "productId 미존재")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SALES','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> create(
            @Valid @RequestBody CreateSlipRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(slipService.create(request, callerOrSystem(callerHeader)));
    }

    /** 헤더 부분 수정 — DRAFT/SAVED 만. */
    @Operation(summary = "헤더 수정", description = "DRAFT/SAVED 단계만. null 필드는 보존")
    @PatchMapping("/{id}/header")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> editHeader(
            @PathVariable UUID id,
            @Valid @RequestBody EditHeaderRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(slipService.editHeader(id, request, callerOrSystem(callerHeader)));
    }

    /** 라인 추가 — DRAFT/SAVED 만. */
    @Operation(summary = "라인 추가", description = "DRAFT/SAVED 단계만")
    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SALES','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> addLine(
            @PathVariable UUID id,
            @Valid @RequestBody AddLineRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(slipService.addLine(id, request, callerOrSystem(callerHeader)));
    }

    /** 라인 제거 — DRAFT/SAVED 만. 204 No Content. */
    @Operation(summary = "라인 제거", description = "DRAFT/SAVED 단계만, orphan removal")
    @DeleteMapping("/{id}/lines/{lineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SALES','MANAGER','MASTER')")
    public void removeLine(
            @PathVariable UUID id,
            @PathVariable UUID lineId,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        slipService.removeLine(id, lineId, callerOrSystem(callerHeader));
    }

    /** DRAFT → SAVED. */
    @Operation(summary = "저장", description = "DRAFT → SAVED")
    @PostMapping("/{id}/save")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> save(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(slipService.save(id, callerOrSystem(callerHeader)));
    }

    /** SAVED → SENT. */
    @Operation(summary = "전송", description = "SAVED → SENT")
    @PostMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> send(@PathVariable UUID id) {
        return ApiResponse.ok(slipService.send(id));
    }

    /** SENT → ACCEPTED. OUTBOUND 면 inventory reserve. */
    @Operation(summary = "수락", description = "SENT → ACCEPTED. OUTBOUND 면 라인별 inventory reserve")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수락 + reserve 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 불일치 또는 재고 부족")
    })
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('WAREHOUSE','INVENTORY','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> accept(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(slipService.accept(id, callerOrSystem(callerHeader)));
    }

    /** ACCEPTED → PROCESSING. */
    @Operation(summary = "처리 시작", description = "ACCEPTED → PROCESSING")
    @PostMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('WAREHOUSE','INVENTORY','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> process(@PathVariable UUID id) {
        return ApiResponse.ok(slipService.process(id));
    }

    /**
     * PROCESSING → INSPECTING — Slice A (sales-polish-2) 신규 단계.
     * 검수자가 picking 결과 검증 시작. inspectorUserId/SignedAt 자동 기입.
     */
    @Operation(summary = "검수 시작",
            description = "PROCESSING → INSPECTING. inspectorUserId/SignedAt 자동 기입 (Slice A 신규)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "검수 시작 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 불일치")
    })
    @PostMapping("/{id}/inspect")
    @PreAuthorize("hasAnyRole('WAREHOUSE','INVENTORY','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> inspect(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(slipService.inspect(id, callerOrSystem(callerHeader)));
    }

    /** INSPECTING → COMPLETED. OUTBOUND 면 deduct, INBOUND 면 inbound. */
    @Operation(summary = "처리 완료", description = "INSPECTING → COMPLETED. OUTBOUND deduct / INBOUND inbound")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "완료 + 재고 갱신 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 불일치 또는 재고 부족")
    })
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('WAREHOUSE','INVENTORY','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> complete(@PathVariable UUID id) {
        return ApiResponse.ok(slipService.complete(id));
    }

    /** COMPLETED → SHIPPING (출고전표 한정). */
    @Operation(summary = "배송 시작", description = "COMPLETED → SHIPPING (OUTBOUND only)")
    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAnyRole('WAREHOUSE','INVENTORY','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> ship(@PathVariable UUID id) {
        return ApiResponse.ok(slipService.ship(id));
    }

    /** SHIPPING → DELIVERED (출고전표 한정). */
    @Operation(summary = "배송 완료", description = "SHIPPING → DELIVERED (OUTBOUND only)")
    @PostMapping("/{id}/deliver")
    @PreAuthorize("hasAnyRole('WAREHOUSE','INVENTORY','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> deliver(@PathVariable UUID id) {
        return ApiResponse.ok(slipService.deliver(id));
    }

    /** 확정 — DELIVERED→CONFIRMED (출고) / COMPLETED→CONFIRMED (입고). */
    @Operation(summary = "확정", description = "출고 DELIVERED→CONFIRMED / 입고 COMPLETED→CONFIRMED")
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> confirm(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(slipService.confirm(id, callerOrSystem(callerHeader)));
    }

    /** 반려 — SENT/ACCEPTED→REJECTED. ACCEPTED 였고 OUTBOUND 면 inventory release. */
    @Operation(summary = "반려", description = "SENT/ACCEPTED → REJECTED. ACCEPTED 였으면 release")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "반려 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "사유 누락")
    })
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(slipService.reject(id, callerOrSystem(callerHeader), request.reason()));
    }

    /** 취소 — DRAFT/SAVED/SENT→CANCELED. */
    @Operation(summary = "취소", description = "DRAFT/SAVED/SENT → CANCELED")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SALES','MANAGER','MASTER')")
    public ApiResponse<SlipDetailResponse> cancel(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(slipService.cancel(id, callerOrSystem(callerHeader)));
    }

    private String callerOrSystem(String header) {
        return (header == null || header.isBlank()) ? "system" : header;
    }
}
