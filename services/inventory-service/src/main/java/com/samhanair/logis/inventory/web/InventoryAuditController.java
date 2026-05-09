package com.samhanair.logis.inventory.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.inventory.domain.AuditStatus;
import com.samhanair.logis.inventory.service.InventoryAuditService;
import com.samhanair.logis.inventory.web.dto.AuditDetailResponse;
import com.samhanair.logis.inventory.web.dto.AuditLineRequest;
import com.samhanair.logis.inventory.web.dto.AuditResponse;
import com.samhanair.logis.inventory.web.dto.CreateAuditRequest;
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
 * 재고 실사 endpoint (Phase 10 P2-6 슬라이스 9). 한국 일반기업회계기준 의무 실사.
 *
 * <p>권한 매트릭스 (memory ROLE 풀네임 의무):
 * <ul>
 *   <li>조회 (GET) — MASTER / MANAGER / DEVELOPER / ACCOUNTANT / WAREHOUSE / INVENTORY</li>
 *   <li>생성 (POST /inventory/audits) — MASTER / MANAGER / INVENTORY</li>
 *   <li>start — MASTER / MANAGER / INVENTORY</li>
 *   <li>라인 입력 (POST/PUT lines) — MASTER / MANAGER / WAREHOUSE / INVENTORY (모바일 작업자 포함)</li>
 *   <li>complete — MASTER / MANAGER / INVENTORY (분개 trigger 권한)</li>
 *   <li>cancel — MASTER / MANAGER / INVENTORY</li>
 * </ul>
 *
 * <p>UUID 비공개 원칙 (memory feedback_uuid_no_user_visibility) — id 는 mutation path key 전용,
 * 사용자 노출 식별자는 auditNo / warehouseCode / productName.
 */
@RestController
@RequestMapping("/inventory/audits")
@RequiredArgsConstructor
public class InventoryAuditController {

    private static final String CALLER_HEADER = "X-User-Id";

    private final InventoryAuditService auditService;

    /**
     * 재고 실사 목록 조회 — warehouse / year / status 필터.
     *
     * @param warehouseId 창고 필터 (null 가능)
     * @param year        연도 필터 (null 가능)
     * @param status      상태 필터 (null 가능)
     * @param page        0-based 페이지 번호
     * @param size        페이지 크기 (기본 20)
     * @return Page&lt;AuditResponse&gt;
     */
    @Operation(summary = "재고 실사 목록", description = "warehouse/year/status 필터 페이지")
    @GetMapping
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','ACCOUNTANT','WAREHOUSE','INVENTORY')")
    public ApiResponse<Page<AuditResponse>> list(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) AuditStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(auditService.list(warehouseId, year, status, pageable));
    }

    /**
     * 재고 실사 단건 상세 (라인 포함).
     *
     * @param id 실사 UUID
     * @return AuditDetailResponse
     */
    @Operation(summary = "재고 실사 단건 상세")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','ACCOUNTANT','WAREHOUSE','INVENTORY')")
    public ApiResponse<AuditDetailResponse> getOne(@PathVariable UUID id) {
        return ApiResponse.ok(auditService.getOne(id));
    }

    /**
     * 재고 실사 신규 등록 — PLANNED 생성 + snapshot 라인 자동 생성.
     *
     * @param request CreateAuditRequest (warehouseId / auditDate)
     * @return AuditDetailResponse (201)
     */
    @Operation(summary = "재고 실사 등록",
            description = "PLANNED 생성. 해당 창고의 모든 활성 stock_balance 를 snapshot 라인으로 자동 생성")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "생성 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "warehouse 미발견")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','INVENTORY')")
    public ApiResponse<AuditDetailResponse> create(
            @Valid @RequestBody CreateAuditRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(auditService.create(request, callerOrSystem(callerHeader)));
    }

    /**
     * 실사 시작 — PLANNED → IN_PROGRESS.
     *
     * @return AuditDetailResponse (200) / CONFLICT (409)
     */
    @Operation(summary = "실사 시작", description = "PLANNED → IN_PROGRESS")
    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','INVENTORY')")
    public ApiResponse<AuditDetailResponse> start(@PathVariable UUID id) {
        return ApiResponse.ok(auditService.start(id));
    }

    /**
     * 라인 입력 (POST) — productId 로 snapshot 라인 검색해 actual_qty set.
     *
     * @param id      실사 UUID
     * @param request AuditLineRequest (productId / actualQty / scanned)
     * @return AuditDetailResponse (200)
     */
    @Operation(summary = "라인 입력 (바코드/수동)",
            description = "productId 로 snapshot 라인 검색해 actual_qty set. scanned=true 면 바코드 스캔")
    @PostMapping("/{id}/lines")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','WAREHOUSE','INVENTORY')")
    public ApiResponse<AuditDetailResponse> recordLine(
            @PathVariable UUID id,
            @Valid @RequestBody AuditLineRequest request) {
        return ApiResponse.ok(auditService.recordLine(id, request));
    }

    /**
     * 라인 수정 (PUT) — lineId path 직접 수정. productId mismatch 검증.
     *
     * @param id      실사 UUID
     * @param lineId  라인 UUID
     * @param request AuditLineRequest
     * @return AuditDetailResponse (200)
     */
    @Operation(summary = "라인 수정", description = "lineId 직접 수정. productId mismatch 검증")
    @PutMapping("/{id}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','WAREHOUSE','INVENTORY')")
    public ApiResponse<AuditDetailResponse> updateLine(
            @PathVariable UUID id,
            @PathVariable UUID lineId,
            @Valid @RequestBody AuditLineRequest request) {
        return ApiResponse.ok(auditService.updateLine(id, lineId, request));
    }

    /**
     * 실사 완료 — IN_PROGRESS → COMPLETED + 차이 자동 분개 trigger + Stock 조정.
     *
     * @return AuditDetailResponse (200) / CONFLICT (409)
     */
    @Operation(summary = "실사 완료",
            description = "IN_PROGRESS → COMPLETED + 차이 자동 분개 (150/919) + Stock 조정")
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','INVENTORY')")
    public ApiResponse<AuditDetailResponse> complete(
            @PathVariable UUID id,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(auditService.complete(id, callerOrSystem(callerHeader)));
    }

    /**
     * 실사 취소 — PLANNED/IN_PROGRESS → CANCELLED. 분개/Stock 조정 안 함.
     *
     * @return AuditDetailResponse (200) / CONFLICT (409)
     */
    @Operation(summary = "실사 취소", description = "PLANNED/IN_PROGRESS → CANCELLED")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','INVENTORY')")
    public ApiResponse<AuditDetailResponse> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(auditService.cancel(id));
    }

    private String callerOrSystem(String header) {
        return (header == null || header.isBlank()) ? "system" : header;
    }
}
